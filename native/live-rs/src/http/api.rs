use std::time::Duration;

use reqwest::header::{HeaderMap, HeaderValue, ACCEPT, ACCEPT_LANGUAGE};

use crate::errors::TikTokLiveError;
use crate::http::parse::{parse_audience, parse_owner_id, parse_room_id, parse_room_info};
use crate::http::ua::system_timezone;
use crate::structs::config::{fetch_locale, fetch_user_agent, Endpoints};
use crate::structs::events::{RoomAudience, RoomInfo};

pub use crate::http::parse::RoomIdResponse;

#[derive(Clone, Debug)]
pub struct FetchParams<'a> {
    pub timeout: Duration,
    pub cookies: Option<&'a str>,
    pub user_agent: Option<&'a str>,
    pub proxy: Option<&'a str>,
    pub language: Option<&'a str>,
    pub region: Option<&'a str>,
    pub endpoints: &'a Endpoints,
}

#[derive(Clone, Copy, Debug)]
pub enum AnchorId<'a> {
    Known(&'a str),
    FromRoomInfo,
}

fn build_client(params: &FetchParams<'_>) -> Result<reqwest::Client, TikTokLiveError> {
    let mut headers = HeaderMap::new();
    headers.insert("Referer", HeaderValue::from_static("https://www.tiktok.com/"));
    for cookies in params.cookies.iter() {
        if !cookies.is_empty() {
            headers.insert("Cookie", HeaderValue::from_str(cookies).map_err(TikTokLiveError::invalid)?);
        }
    }
    let mut builder = reqwest::Client::builder().timeout(params.timeout).user_agent(fetch_user_agent(params)).default_headers(headers);
    for proxy in params.proxy.iter() {
        builder = builder.proxy(reqwest::Proxy::all(*proxy)?);
    }
    Ok(builder.build()?)
}

async fn get(params: &FetchParams<'_>, url: &str) -> Result<(u16, String), TikTokLiveError> {
    let resp = build_client(params)?.get(url).send().await?;
    let status = resp.status().as_u16();
    Ok((status, resp.text().await?))
}

pub async fn fetch_room_id(username: &str, params: FetchParams<'_>) -> Result<RoomIdResponse, TikTokLiveError> {
    let clean = username.trim().trim_start_matches('@');
    let locale = fetch_locale(&params);
    let mut url = reqwest::Url::parse(&format!("{}api-live/user/room", params.endpoints.web))
        .map_err(TikTokLiveError::invalid)?;
    url.query_pairs_mut()
        .append_pair("aid", "1988")
        .append_pair("app_name", "tiktok_web")
        .append_pair("device_platform", "web_pc")
        .append_pair("app_language", &locale.language)
        .append_pair("browser_language", &locale.browser_language)
        .append_pair("region", &locale.region)
        .append_pair("user_is_login", "false")
        .append_pair("uniqueId", clean)
        .append_pair("sourceType", "54")
        .append_pair("staleTime", "600000");

    match get(&params, url.as_str()).await {
        Ok((status, body)) => match parse_room_id(clean, status, &body) {
            Ok(room) => Ok(room),
            Err(api_error) => {
                tracing::warn!("TikTok room API lookup failed; trying LIVE page fallback: {api_error}");
                fetch_room_id_from_html(clean, &params).await
                    .map_err(|html_error| TikTokLiveError::invalid(format!("room lookup failed; API: {api_error}; HTML: {html_error}")))
            }
        },
        Err(api_error) => {
            tracing::warn!("TikTok room API request failed; trying LIVE page fallback: {api_error}");
            fetch_room_id_from_html(clean, &params).await
                .map_err(|html_error| TikTokLiveError::invalid(format!("room lookup failed; API: {api_error}; HTML: {html_error}")))
        }
    }
}


async fn fetch_room_id_from_html(username: &str, params: &FetchParams<'_>) -> Result<RoomIdResponse, TikTokLiveError> {
    let locale = fetch_locale(params);
    let mut headers = HeaderMap::new();
    headers.insert(ACCEPT, HeaderValue::from_static("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
    headers.insert(ACCEPT_LANGUAGE, HeaderValue::from_str(&format!("{}-{},{};q=0.9", locale.language, locale.region, locale.language)).map_err(TikTokLiveError::invalid)?);
    let mut builder = reqwest::Client::builder()
        .timeout(params.timeout)
        .user_agent(fetch_user_agent(params))
        .default_headers(headers);
    for proxy in params.proxy.iter() {
        builder = builder.proxy(reqwest::Proxy::all(*proxy)?);
    }
    let url = format!("{}@{}/live", params.endpoints.web, urlencoding::encode(username));
    let response = builder.build()?.get(url).send().await?;
    let status = response.status().as_u16();
    if !response.status().is_success() {
        return Err(TikTokLiveError::invalid(format!("LIVE page returned HTTP {status}")));
    }
    let html = response.text().await?;
    let marker = r#"id="__UNIVERSAL_DATA_FOR_REHYDRATION__""#;
    let marker_at = html.find(marker).ok_or_else(|| TikTokLiveError::invalid("LIVE page has no universal data marker"))?;
    let after = &html[marker_at..];
    let start = marker_at + after.find('>').ok_or_else(|| TikTokLiveError::invalid("malformed LIVE page script"))? + 1;
    let end = start + html[start..].find("</script>").ok_or_else(|| TikTokLiveError::invalid("LIVE page data script is incomplete"))?;
    let json: serde_json::Value = serde_json::from_str(&html[start..end])?;
    let detail = json.pointer("/__DEFAULT_SCOPE__/webapp.user-detail")
        .ok_or_else(|| TikTokLiveError::invalid("LIVE page has no user-detail data"))?;
    let user = detail.pointer("/userInfo/user")
        .ok_or_else(|| TikTokLiveError::invalid("LIVE page has no user data"))?;
    let room_id = user.get("roomId").and_then(|v| v.as_str().map(str::to_owned).or_else(|| v.as_u64().map(|n| n.to_string())))
        .filter(|id| !id.is_empty() && id != "0")
        .ok_or_else(|| TikTokLiveError::HostNotOnline("LIVE page contains no active room ID".into()))?;
    let status = user.get("roomId").and_then(|_| detail.pointer("/userInfo/user/status").and_then(|v| v.as_i64()))
        .or_else(|| detail.pointer("/userInfo/liveRoom/status").and_then(|v| v.as_i64()));
    if let Some(status) = status {
        if status != 2 {
            return Err(TikTokLiveError::HostNotOnline(format!("LIVE page status={status}")));
        }
    }
    Ok(RoomIdResponse {
        room_id,
        anchor_id: user.get("id").and_then(|v| v.as_str().map(str::to_owned).or_else(|| v.as_u64().map(|n| n.to_string()))).unwrap_or_default(),
    })
}

pub async fn fetch_room_info(room_id: &str, params: FetchParams<'_>) -> Result<RoomInfo, TikTokLiveError> {
    let locale = fetch_locale(&params);
    let tz = system_timezone();
    let url = format!(
        "{}room/info/?aid=1988&app_name=tiktok_web&device_platform=web_pc&app_language={lang}&browser_language={}&browser_name=Mozilla&browser_online=true&browser_platform=Win32&browser_version=5.0+(Windows+NT+10.0%3B+Win64%3B+x64)&cookie_enabled=true&focus_state=true&from_page=user&screen_height=1080&screen_width=1920&tz_name={}&webcast_language={lang}&room_id={room_id}",
        params.endpoints.webcast,
        locale.browser_language,
        urlencoding::encode(&tz),
        lang = locale.language,
    );
    let (status, body) = get(&params, &url).await?;
    parse_room_info(status, &body)
}

pub async fn fetch_room_audience(room_id: &str, anchor: AnchorId<'_>, params: FetchParams<'_>) -> Result<RoomAudience, TikTokLiveError> {
    let anchor_id = match anchor {
        AnchorId::Known(id) => id.to_string(),
        AnchorId::FromRoomInfo => parse_owner_id(&fetch_room_info(room_id, params.clone()).await?.raw_json)?,
    };
    let locale = fetch_locale(&params);
    let url = format!(
        "{}ranklist/online_audience/?aid=1988&app_name=tiktok_web&device_platform=web_pc&app_language={}&browser_language={}&channel=tiktok_web&room_id={room_id}&anchor_id={anchor_id}",
        params.endpoints.webcast, locale.language, locale.browser_language
    );
    let (status, body) = get(&params, &url).await?;
    parse_audience(status, &body)
}
