use jni::objects::{JClass, JString, JValue};
use jni::sys::jboolean;
use jni::{JNIEnv, JavaVM};
use std::sync::atomic::{AtomicU64, Ordering};
use std::thread;
use tokio::time::{sleep, Duration};
use livecraft_tiktok_core::{TikTokLive, structs::TikTokLiveEvent};

static GENERATION: AtomicU64 = AtomicU64::new(0);

fn is_current(generation: u64) -> bool {
    GENERATION.load(Ordering::SeqCst) == generation
}

fn status(vm: &JavaVM, message: &str) {
    if let Ok(mut env) = vm.attach_current_thread() {
        if let (Ok(class), Ok(value)) = (
            env.find_class("com/livecraft/GameBridgeService"),
            env.new_string(message),
        ) {
            let _ = env.call_static_method(
                class,
                "onNativeTikTokStatus",
                "(Ljava/lang/String;)V",
                &[JValue::Object(&value)],
            );
        }
    }
}

fn chat(vm: &JavaVM, username: &str, comment: &str) {
    if let Ok(mut env) = vm.attach_current_thread() {
        if let (Ok(user), Ok(message), Ok(class)) = (
            env.new_string(username),
            env.new_string(comment),
            env.find_class("com/livecraft/GameBridgeService"),
        ) {
            let _ = env.call_static_method(
                class,
                "onNativeTikTokChat",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                &[JValue::Object(&user), JValue::Object(&message)],
            );
        }
    }
}

fn gift(vm: &JavaVM, name: &str, coins: i64, combo: bool, streak_over: bool) {
    if let Ok(mut env) = vm.attach_current_thread() {
        if let (Ok(class), Ok(value)) = (
            env.find_class("com/livecraft/GameBridgeService"),
            env.new_string(name),
        ) {
            let _ = env.call_static_method(
                class,
                "onNativeTikTokGift",
                "(Ljava/lang/String;JZZ)V",
                &[
                    JValue::Object(&value),
                    JValue::Long(coins),
                    JValue::Bool(combo as jboolean),
                    JValue::Bool(streak_over as jboolean),
                ],
            );
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livecraft_TikTokNative_nativeStart(
    mut env: JNIEnv,
    _class: JClass,
    username: JString,
) {
    let generation = GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    let username: String = match env.get_string(&username) {
        Ok(value) => value.into(),
        Err(_) => return,
    };
    let vm = match env.get_java_vm() {
        Ok(value) => value,
        Err(_) => return,
    };

    thread::spawn(move || {
        let runtime = match tokio::runtime::Builder::new_multi_thread().enable_all().build() {
            Ok(value) => value,
            Err(error) => {
                status(&vm, &format!("Falha ao iniciar o conector TikTok: {error}"));
                return;
            }
        };

        runtime.block_on(async move {
            let mut retry: u32 = 0;

            while is_current(generation) {
                status(&vm, &format!("Localizando a LIVE de @{username}..."));

                match TikTokLive::builder(&username).connect().await {
                    Ok(mut stream) => {
                        status(&vm, "Sala localizada. Aguardando conexão segura com a LIVE...");
                        let mut reconnect = false;

                        while is_current(generation) {
                            tokio::select! {
                                _ = sleep(Duration::from_millis(250)) => {},
                                event = stream.next_event() => {
                                    match event {
                                        Ok(TikTokLiveEvent::Connected { .. }) => {
                                            retry = 0;
                                            status(&vm, &format!("Conectado à live de @{username}. Aguardando eventos."));
                                        }
                                        Ok(TikTokLiveEvent::Chat(event)) => {
                                            let author = event.user.as_ref()
                                                .map(|user| if !user.nickname.is_empty() {
                                                    user.nickname.as_str()
                                                } else {
                                                    user.unique_id.as_str()
                                                })
                                                .unwrap_or("TikTok");
                                            chat(&vm, author, &event.comment);
                                        }
                                        Ok(TikTokLiveEvent::Gift(event)) => {
                                            let name = event.gift_details.as_ref()
                                                .map(|details| details.gift_name.as_str())
                                                .unwrap_or("Presente");
                                            gift(&vm, name, event.diamond_total(), event.is_combo_gift(), event.is_streak_over());
                                        }
                                        Ok(TikTokLiveEvent::Reconnecting { attempt, max_retries, delay_secs }) => {
                                            status(&vm, &format!("LIVE instável. Reconectando ({attempt}/{max_retries}) em {delay_secs}s..."));
                                        }
                                        Ok(TikTokLiveEvent::Disconnected) => {
                                            status(&vm, "Conexão com a LIVE encerrada. Tentando novamente...");
                                            reconnect = true;
                                            break;
                                        }
                                        Ok(_) => {},
                                        Err(error) => {
                                            status(&vm, &format!("Fluxo TikTok encerrado: {error}. Tentando novamente..."));
                                            reconnect = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        drop(stream);
                        if !is_current(generation) {
                            break;
                        }
                        if !reconnect {
                            continue;
                        }
                    }
                    Err(error) => {
                        status(&vm, &format!("Não foi possível localizar/conectar à LIVE: {error}"));
                    }
                }

                if !is_current(generation) {
                    break;
                }
                retry = retry.saturating_add(1);
                let delay = (2u64.saturating_pow(retry.min(4))).min(30);
                status(&vm, &format!("Nova tentativa de conexão em {delay}s (tentativa {retry})..."));
                sleep(Duration::from_secs(delay)).await;
            }

            status(&vm, "Conector TikTok parado.");
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_livecraft_TikTokNative_nativeStop(
    _env: JNIEnv,
    _class: JClass,
) {
    GENERATION.fetch_add(1, Ordering::SeqCst);
}
