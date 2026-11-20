use std::sync::Arc;
use std::time::Duration;

use reqwest::cookie::{CookieStore, Jar};
use reqwest::header::HeaderMap;
use reqwest::Url;

use crate::errors::TikTokLiveError;

#[derive(Clone, Debug)]
pub struct TtwidRequest<'a> {
    pub url: &'a str,
    pub timeout: Duration,
    pub user_agent: &'a str,
    pub proxy: Option<&'a str>,
    pub attempts: u32,
    pub retry_delay: Duration,
}

pub async fn fetch_ttwid(request: &TtwidRequest<'_>) -> Result<String, TikTokLiveError> {
    let cookie_jar = Arc::new(Jar::default());
    let client = build_client(request, cookie_jar.clone())?;
    let cookie_url = Url::parse(request.url).map_err(TikTokLiveError::invalid)?;
    let mut attempt: u32 = 0;

    loop {
        attempt += 1;
        let resp = client.get(request.url).send().await?;
        let status = resp.status();
        if let Some(ttwid) = find_ttwid(resp.headers())? {
            return Ok(ttwid);
        }

        // TikTok may set ttwid on an intermediate redirect. reqwest's cookie
        // jar stores cookies from every response in the redirect chain.
        if let Some(cookie) = cookie_jar.cookies(&cookie_url) {
            if let Ok(value) = cookie.to_str() {
                if let Some(ttwid) = find_ttwid_cookie_header(value) {
                    return Ok(ttwid);
                }
            }
        }

        if attempt >= request.attempts {
            return Err(TikTokLiveError::ttwid(format!("no ttwid cookie after {attempt} attempts (last http {status})")));
        }
        tracing::warn!(attempt, %status, "no ttwid cookie in response or cookie jar, retrying");
        tokio::time::sleep(request.retry_delay).await;
    }
}

fn build_client(request: &TtwidRequest<'_>, jar: Arc<Jar>) -> Result<reqwest::Client, TikTokLiveError> {
    let mut builder = reqwest::Client::builder()
        .timeout(request.timeout)
        .user_agent(request.user_agent)
        .cookie_provider(jar)
        .redirect(reqwest::redirect::Policy::limited(10));
    for proxy in request.proxy.iter() {
        builder = builder.proxy(reqwest::Proxy::all(*proxy)?);
    }
    Ok(builder.build()?)
}


fn find_ttwid(headers: &HeaderMap) -> Result<Option<String>, TikTokLiveError> {
    for header in headers.get_all("set-cookie") {
        let value = header.to_str().map_err(|e| TikTokLiveError::invalid(format!("set-cookie header: {e}")))?;
        if let Some(token) = find_ttwid_cookie_header(value) {
            return Ok(Some(token));
        }
    }
    Ok(None)
}

fn find_ttwid_cookie_header(value: &str) -> Option<String> {
    value.split(';').find_map(|part| {
        let (name, value) = part.trim().split_once('=')?;
        if name.eq_ignore_ascii_case("ttwid") && !value.is_empty() {
            Some(value.to_string())
        } else {
            None
        }
    })
}
