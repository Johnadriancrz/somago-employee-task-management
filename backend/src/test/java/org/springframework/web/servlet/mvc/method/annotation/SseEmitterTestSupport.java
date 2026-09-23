package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.http.MediaType;

/**
 * Test-only helper deliberately placed in the same package as Spring's
 * {@link ResponseBodyEmitter} so it can call the package-private
 * {@code initialize(Handler)} — the hook Spring MVC's real async request
 * machinery uses to wire an emitter's {@code onCompletion}/{@code onTimeout}
 * callbacks to the actual request lifecycle. Without a real HTTP round
 * trip, those callbacks are otherwise never invoked (calling
 * {@code emitter.complete()} directly is a no-op until a handler has been
 * installed), so this is needed to verify our cleanup-on-completion logic
 * (see {@code ChatMessageBroadcasterTest}) without risking a hung async
 * request in a full MockMvc dispatch.
 */
public final class SseEmitterTestSupport {

    private SseEmitterTestSupport() {
    }

    /**
     * Installs a no-op {@code Handler} and returns the completion callback
     * that a real async request would invoke once it actually completes —
     * i.e. exactly what {@code emitter.onCompletion(...)} registered.
     */
    public static Runnable captureCompletionCallback(ResponseBodyEmitter emitter) throws IOException {
        Runnable[] captured = new Runnable[1];
        emitter.initialize(new ResponseBodyEmitter.Handler() {
            @Override
            public void send(Object data, MediaType mediaType) {
            }

            @Override
            public void send(Set<ResponseBodyEmitter.DataWithMediaType> data) {
            }

            @Override
            public void complete() {
            }

            @Override
            public void completeWithError(Throwable failure) {
            }

            @Override
            public void onTimeout(Runnable callback) {
            }

            @Override
            public void onError(Consumer<Throwable> callback) {
            }

            @Override
            public void onCompletion(Runnable callback) {
                captured[0] = callback;
            }
        });
        return captured[0];
    }
}
