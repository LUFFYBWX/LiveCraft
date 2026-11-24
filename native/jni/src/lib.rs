use jni::objects::{GlobalRef, JClass, JString, JValue};
use jni::sys::jboolean;
use jni::{JNIEnv, JavaVM};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::thread;
use tokio::time::{sleep, Duration};
use livecraft_tiktok_core::{TikTokLive, structs::TikTokLiveEvent};

static GENERATION: AtomicU64 = AtomicU64::new(0);

fn is_current(generation: u64) -> bool {
    GENERATION.load(Ordering::SeqCst) == generation
}

fn status(vm: &JavaVM, class: &GlobalRef, message: &str) {
    if let Ok(mut env) = vm.attach_current_thread() {
        if let Ok(value) = env.new_string(message) {
            let class = <&JClass>::from(class.as_obj());
            let _ = env.call_static_method(class, "onNativeTikTokStatus", "(Ljava/lang/String;)V", &[JValue::Object(&value)]);
        }
    }
}

fn diagnostic(vm: &JavaVM, class: &GlobalRef, event_type: &str) {
    if let Ok(mut env) = vm.attach_current_thread() {
        if let Ok(value) = env.new_string(event_type) {
            let class = <&JClass>::from(class.as_obj());
            let _ = env.call_static_method(class, "onNativeTikTokDiagnostic", "(Ljava/lang/String;)V", &[JValue::Object(&value)]);
        }
    }
}

fn chat(vm: &JavaVM, class: &GlobalRef, username: &str, comment: &str) {
    if let Ok(mut env) = vm.attach_current_thread() {
        if let (Ok(user), Ok(message)) = (env.new_string(username), env.new_string(comment)) {
            let class = <&JClass>::from(class.as_obj());
            let _ = env.call_static_method(class, "onNativeTikTokChat", "(Ljava/lang/String;Ljava/lang/String;)V", &[JValue::Object(&user), JValue::Object(&message)]);
        }
    }
}

fn likes(vm: &JavaVM, class: &GlobalRef, count: i32) {
    if let Ok(mut env) = vm.attach_current_thread() {
        let class = <&JClass>::from(class.as_obj());
        let _ = env.call_static_method(class, "onNativeTikTokLikes", "(I)V", &[JValue::Int(count)]);
    }
}

fn gift(vm: &JavaVM, class: &GlobalRef, name: &str, image_url: &str, coins: i64, combo: bool, streak_over: bool, gift_count: i32) {
    if let Ok(mut env) = vm.attach_current_thread() {
        if let (Ok(value), Ok(image)) = (env.new_string(name), env.new_string(image_url)) {
            let class = <&JClass>::from(class.as_obj());
            let _ = env.call_static_method(class, "onNativeTikTokGift", "(Ljava/lang/String;Ljava/lang/String;JZZI)V", &[
                JValue::Object(&value), JValue::Object(&image), JValue::Long(coins),
                JValue::Bool(combo as jboolean), JValue::Bool(streak_over as jboolean), JValue::Int(gift_count),
            ]);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livecraft_TikTokNative_nativeStart(mut env: JNIEnv, _class: JClass, username: JString) {
    let generation = GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    let username: String = match env.get_string(&username) { Ok(value) => value.into(), Err(_) => return };
    let vm = match env.get_java_vm() { Ok(value) => value, Err(_) => return };
    let bridge_class = match env.find_class("com/livecraft/GameBridgeService").and_then(|local| env.new_global_ref(local)) {
        Ok(value) => Arc::new(value),
        Err(_) => return,
    };

    let vm = Arc::new(vm);
    status(&vm, &bridge_class, "Checkpoint JNI 1: solicitação e VM obtidas.");
    status(&vm, &bridge_class, "Checkpoint JNI 2: tentando criar thread Rust...");
    let thread_vm = Arc::clone(&vm);
    let thread_class = Arc::clone(&bridge_class);

    let spawn_result = thread::Builder::new().name("livecraft-tiktok".into()).spawn(move || {
        status(&vm, &thread_class, "Checkpoint thread 3: thread Rust entrou.");
        status(&vm, &thread_class, "Checkpoint runtime 4: preparando runtime Tokio...");
        let panic_vm = Arc::clone(&vm);
        let panic_class = Arc::clone(&thread_class);

        let worker_result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let runtime = tokio::runtime::Builder::new_multi_thread().enable_all().build()
                .map_err(|error| format!("Não foi possível preparar o runtime: {error}"))?;
            status(&vm, &thread_class, "Runtime pronto. Iniciando busca da sala no TikTok...");

            runtime.block_on(async move {
                let mut retry: u32 = 0;
                while is_current(generation) {
                    status(&vm, &thread_class, &format!("Consultando o TikTok para localizar a LIVE de @{username}..."));
                    match TikTokLive::builder(&username).connect().await {
                        Ok(mut stream) => {
                            status(&vm, &thread_class, "Sala encontrada. Aguardando o WebSocket do TikTok...");
                            let mut reconnect = false;
                            while is_current(generation) {
                                tokio::select! {
                                    _ = sleep(Duration::from_millis(250)) => {},
                                    event = stream.next_event() => {
                                        match event {
                                            Ok(TikTokLiveEvent::Connected { .. }) => {
                                                retry = 0;
                                                status(&vm, &thread_class, &format!("Conectado à live de @{username}. Aguardando eventos."));
                                            }
                                            Ok(TikTokLiveEvent::Chat(event)) => {
                                                let author = event.user.as_ref().map(|user| if !user.nickname.is_empty() { user.nickname.as_str() } else { user.unique_id.as_str() }).unwrap_or("TikTok");
                                                chat(&vm, &thread_class, author, &event.comment);
                                            }
                                            Ok(TikTokLiveEvent::Gift(event)) => {
                                                let details = event.gift_details.as_ref();
                                                let name = details.map(|gift| gift.gift_name.as_str()).unwrap_or("Presente");
                                                let image_url = details
                                                    .and_then(|gift| gift.gift_image.as_ref().or(gift.icon.as_ref()))
                                                    .and_then(|image| image.url_list.first())
                                                    .map(String::as_str)
                                                    .unwrap_or("");
                                                gift(&vm, &thread_class, name, image_url, event.diamond_total(), event.is_combo_gift(), event.is_streak_over(), if event.is_combo_gift() { event.repeat_count.max(1) } else { 1 });
                                            }
                                            Ok(TikTokLiveEvent::Like(event)) => {
                                                likes(&vm, &thread_class, event.like_count);
                                            }
                                            Ok(TikTokLiveEvent::Reconnecting { attempt, max_retries, delay_secs }) => {
                                                status(&vm, &thread_class, &format!("LIVE instável. Reconectando ({attempt}/{max_retries}) em {delay_secs}s..."));
                                            }
                                            Ok(TikTokLiveEvent::Status(message)) => {
                                                status(&vm, &thread_class, &message);
                                            }
                                            Ok(TikTokLiveEvent::Disconnected) => {
                                                status(&vm, &thread_class, "TikTok encerrou o WebSocket. Preparando reconexão...");
                                                reconnect = true;
                                                break;
                                            }
                                            Ok(TikTokLiveEvent::Unknown { method, .. }) => diagnostic(&vm, &thread_class, &method),
                                            Ok(_) => {},
                                            Err(error) => {
                                                status(&vm, &thread_class, &format!("Erro no fluxo de eventos do TikTok: {error}"));
                                                reconnect = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            drop(stream);
                            if !is_current(generation) { break; }
                            if !reconnect { continue; }
                        }
                        Err(error) => status(&vm, &thread_class, &format!("Falha ao localizar/conectar à LIVE: {error}")),
                    }
                    if !is_current(generation) { break; }
                    retry = retry.saturating_add(1);
                    let delay = (2u64.saturating_pow(retry.min(4))).min(30);
                    status(&vm, &thread_class, &format!("Nova tentativa em {delay}s (tentativa {retry})..."));
                    sleep(Duration::from_secs(delay)).await;
                }
                status(&vm, &thread_class, "Conector TikTok parado.");
            });
            Ok::<(), String>(())
        }));

        match worker_result {
            Ok(Ok(())) => {},
            Ok(Err(error)) => status(&panic_vm, &panic_class, &format!("Falha na inicialização nativa: {error}")),
            Err(payload) => {
                let reason = payload.downcast_ref::<String>().map(String::as_str)
                    .or_else(|| payload.downcast_ref::<&str>().copied())
                    .unwrap_or("falha interna inesperada no conector");
                status(&panic_vm, &panic_class, &format!("Falha interna do conector TikTok: {reason}"));
            }
        }
    });

    match spawn_result {
        Ok(_) => status(&thread_vm, &bridge_class, "Checkpoint JNI 5: thread criada."),
        Err(error) => status(&thread_vm, &bridge_class, &format!("Falha ao criar thread Rust: {error}")),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livecraft_TikTokNative_nativeStop(_env: JNIEnv, _class: JClass) {
    GENERATION.fetch_add(1, Ordering::SeqCst);
}
