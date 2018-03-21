/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @build DummyWebSocketServer
 * @run testng/othervm
 *      -Djdk.internal.httpclient.websocket.debug=true
 *       WebSocketTest
 */

import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.WebSocket.NORMAL_CLOSURE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class WebSocketTest {

    private static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    private static final Class<IllegalStateException> ISE = IllegalStateException.class;
    private static final Class<IOException> IOE = IOException.class;

    /* shortcut */
    private static void assertFails(Class<? extends Throwable> clazz,
                                    CompletionStage<?> stage) {
        Support.assertCompletesExceptionally(clazz, stage);
    }

    private DummyWebSocketServer server;
    private WebSocket webSocket;

    @AfterTest
    public void cleanup() {
        server.close();
        webSocket.abort();
    }

    @Test
    public void illegalArgument() throws IOException {
        server = new DummyWebSocketServer();
        server.open();
        webSocket = newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();

        assertFails(IAE, webSocket.sendPing(ByteBuffer.allocate(126)));
        assertFails(IAE, webSocket.sendPing(ByteBuffer.allocate(127)));
        assertFails(IAE, webSocket.sendPing(ByteBuffer.allocate(128)));
        assertFails(IAE, webSocket.sendPing(ByteBuffer.allocate(129)));
        assertFails(IAE, webSocket.sendPing(ByteBuffer.allocate(256)));

        assertFails(IAE, webSocket.sendPong(ByteBuffer.allocate(126)));
        assertFails(IAE, webSocket.sendPong(ByteBuffer.allocate(127)));
        assertFails(IAE, webSocket.sendPong(ByteBuffer.allocate(128)));
        assertFails(IAE, webSocket.sendPong(ByteBuffer.allocate(129)));
        assertFails(IAE, webSocket.sendPong(ByteBuffer.allocate(256)));

        assertFails(IOE, webSocket.sendText(Support.incompleteString(), true));
        assertFails(IOE, webSocket.sendText(Support.incompleteString(), false));
        assertFails(IOE, webSocket.sendText(Support.malformedString(), true));
        assertFails(IOE, webSocket.sendText(Support.malformedString(), false));

        assertFails(IOE, webSocket.sendClose(NORMAL_CLOSURE, Support.stringWithNBytes(124)));
        assertFails(IOE, webSocket.sendClose(NORMAL_CLOSURE, Support.stringWithNBytes(125)));
        assertFails(IOE, webSocket.sendClose(NORMAL_CLOSURE, Support.stringWithNBytes(128)));
        assertFails(IOE, webSocket.sendClose(NORMAL_CLOSURE, Support.stringWithNBytes(256)));
        assertFails(IOE, webSocket.sendClose(NORMAL_CLOSURE, Support.stringWithNBytes(257)));
        assertFails(IOE, webSocket.sendClose(NORMAL_CLOSURE, Support.stringWith2NBytes((123 / 2) + 1)));
        assertFails(IOE, webSocket.sendClose(NORMAL_CLOSURE, Support.malformedString()));
        assertFails(IOE, webSocket.sendClose(NORMAL_CLOSURE, Support.incompleteString()));

        assertFails(IAE, webSocket.sendClose(-2, "a reason"));
        assertFails(IAE, webSocket.sendClose(-1, "a reason"));
        assertFails(IAE, webSocket.sendClose(0, "a reason"));
        assertFails(IAE, webSocket.sendClose(1, "a reason"));
        assertFails(IAE, webSocket.sendClose(500, "a reason"));
        assertFails(IAE, webSocket.sendClose(998, "a reason"));
        assertFails(IAE, webSocket.sendClose(999, "a reason"));
        assertFails(IAE, webSocket.sendClose(1002, "a reason"));
        assertFails(IAE, webSocket.sendClose(1003, "a reason"));
        assertFails(IAE, webSocket.sendClose(1006, "a reason"));
        assertFails(IAE, webSocket.sendClose(1007, "a reason"));
        assertFails(IAE, webSocket.sendClose(1009, "a reason"));
        assertFails(IAE, webSocket.sendClose(1010, "a reason"));
        assertFails(IAE, webSocket.sendClose(1012, "a reason"));
        assertFails(IAE, webSocket.sendClose(1013, "a reason"));
        assertFails(IAE, webSocket.sendClose(1015, "a reason"));
        assertFails(IAE, webSocket.sendClose(5000, "a reason"));
        assertFails(IAE, webSocket.sendClose(32768, "a reason"));
        assertFails(IAE, webSocket.sendClose(65535, "a reason"));
        assertFails(IAE, webSocket.sendClose(65536, "a reason"));
        assertFails(IAE, webSocket.sendClose(Integer.MAX_VALUE, "a reason"));
        assertFails(IAE, webSocket.sendClose(Integer.MIN_VALUE, "a reason"));

        assertThrows(IAE, () -> webSocket.request(Integer.MIN_VALUE));
        assertThrows(IAE, () -> webSocket.request(Long.MIN_VALUE));
        assertThrows(IAE, () -> webSocket.request(-1));
        assertThrows(IAE, () -> webSocket.request(0));
    }

    @Test
    public void partialBinaryThenText() throws IOException {
        server = new DummyWebSocketServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        webSocket.sendBinary(ByteBuffer.allocate(16), false).join();
        assertFails(ISE, webSocket.sendText("text", false));
        assertFails(ISE, webSocket.sendText("text", true));
        // Pings & Pongs are fine
        webSocket.sendPing(ByteBuffer.allocate(125)).join();
        webSocket.sendPong(ByteBuffer.allocate(125)).join();
    }

    @Test
    public void partialTextThenBinary() throws IOException {
        server = new DummyWebSocketServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();

        webSocket.sendText("text", false).join();
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(16), false));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(16), true));
        // Pings & Pongs are fine
        webSocket.sendPing(ByteBuffer.allocate(125)).join();
        webSocket.sendPong(ByteBuffer.allocate(125)).join();
    }

    @Test
    public void sendMethodsThrowIOE1() throws IOException {
        server = new DummyWebSocketServer();
        server.open();
        webSocket = newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();

        webSocket.sendClose(NORMAL_CLOSURE, "ok").join();

        assertFails(IOE, webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok"));

        assertFails(IOE, webSocket.sendText("", true));
        assertFails(IOE, webSocket.sendText("", false));
        assertFails(IOE, webSocket.sendText("abc", true));
        assertFails(IOE, webSocket.sendText("abc", false));
        assertFails(IOE, webSocket.sendBinary(ByteBuffer.allocate(0), true));
        assertFails(IOE, webSocket.sendBinary(ByteBuffer.allocate(0), false));
        assertFails(IOE, webSocket.sendBinary(ByteBuffer.allocate(1), true));
        assertFails(IOE, webSocket.sendBinary(ByteBuffer.allocate(1), false));

        assertFails(IOE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(IOE, webSocket.sendPing(ByteBuffer.allocate(124)));
        assertFails(IOE, webSocket.sendPing(ByteBuffer.allocate(1)));
        assertFails(IOE, webSocket.sendPing(ByteBuffer.allocate(0)));

        assertFails(IOE, webSocket.sendPong(ByteBuffer.allocate(125)));
        assertFails(IOE, webSocket.sendPong(ByteBuffer.allocate(124)));
        assertFails(IOE, webSocket.sendPong(ByteBuffer.allocate(1)));
        assertFails(IOE, webSocket.sendPong(ByteBuffer.allocate(0)));
    }

    @Test
    public void sendMethodsThrowIOE2() throws Exception {
        server = Support.serverWithCannedData(0x88, 0x00);
        server.open();
        CompletableFuture<Void> onCloseCalled = new CompletableFuture<>();
        CompletableFuture<Void> canClose = new CompletableFuture<>();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onClose(WebSocket webSocket,
                                              int statusCode,
                                              String reason) {
                System.out.printf("onClose(%s, '%s')%n", statusCode, reason);
                onCloseCalled.complete(null);
                return canClose;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                System.out.println("onError(" + error + ")");
                onCloseCalled.completeExceptionally(error);
            }
        };

        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), listener)
                .join();

        onCloseCalled.join();      // Wait for onClose to be called
        canClose.complete(null);   // Signal to the WebSocket it can close the output
        TimeUnit.SECONDS.sleep(5); // Give canClose some time to reach the WebSocket

        assertFails(IOE, webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok"));

        assertFails(IOE, webSocket.sendText("", true));
        assertFails(IOE, webSocket.sendText("", false));
        assertFails(IOE, webSocket.sendText("abc", true));
        assertFails(IOE, webSocket.sendText("abc", false));
        assertFails(IOE, webSocket.sendBinary(ByteBuffer.allocate(0), true));
        assertFails(IOE, webSocket.sendBinary(ByteBuffer.allocate(0), false));
        assertFails(IOE, webSocket.sendBinary(ByteBuffer.allocate(1), true));
        assertFails(IOE, webSocket.sendBinary(ByteBuffer.allocate(1), false));

        assertFails(IOE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(IOE, webSocket.sendPing(ByteBuffer.allocate(124)));
        assertFails(IOE, webSocket.sendPing(ByteBuffer.allocate(1)));
        assertFails(IOE, webSocket.sendPing(ByteBuffer.allocate(0)));

        assertFails(IOE, webSocket.sendPong(ByteBuffer.allocate(125)));
        assertFails(IOE, webSocket.sendPong(ByteBuffer.allocate(124)));
        assertFails(IOE, webSocket.sendPong(ByteBuffer.allocate(1)));
        assertFails(IOE, webSocket.sendPong(ByteBuffer.allocate(0)));
    }

    @Test
    public void simpleAggregatingBinaryMessages() throws IOException {
        List<byte[]> expected = List.of("alpha", "beta", "gamma", "delta")
                .stream()
                .map(s -> s.getBytes(StandardCharsets.US_ASCII))
                .collect(Collectors.toList());
        int[] binary = new int[]{
                0x82, 0x05, 0x61, 0x6c, 0x70, 0x68, 0x61, // [alpha]
                0x02, 0x02, 0x62, 0x65,                   // [be
                0x80, 0x02, 0x74, 0x61,                   // ta]
                0x02, 0x01, 0x67,                         // [g
                0x00, 0x01, 0x61,                         // a
                0x00, 0x00,                               //
                0x00, 0x00,                               //
                0x00, 0x01, 0x6d,                         // m
                0x00, 0x01, 0x6d,                         // m
                0x80, 0x01, 0x61,                         // a]
                0x8a, 0x00,                               // <PONG>
                0x02, 0x04, 0x64, 0x65, 0x6c, 0x74,       // [delt
                0x00, 0x01, 0x61,                         // a
                0x80, 0x00,                               // ]
                0x88, 0x00                                // <CLOSE>
        };
        CompletableFuture<List<byte[]>> actual = new CompletableFuture<>();

        server = Support.serverWithCannedData(binary);
        server.open();

        WebSocket.Listener listener = new WebSocket.Listener() {

            List<byte[]> collectedBytes = new ArrayList<>();
            ByteBuffer buffer = ByteBuffer.allocate(1024);

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket,
                                               ByteBuffer message,
                                               boolean last) {
                System.out.printf("onBinary(%s, %s)%n", message, last);
                webSocket.request(1);

                append(message);
                if (last) {
                    buffer.flip();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    buffer.clear();
                    processWholeBinary(bytes);
                }
                return null;
            }

            private void append(ByteBuffer message) {
                if (buffer.remaining() < message.remaining()) {
                    assert message.remaining() > 0;
                    int cap = (buffer.capacity() + message.remaining()) * 2;
                    ByteBuffer b = ByteBuffer.allocate(cap);
                    b.put(buffer.flip());
                    buffer = b;
                }
                buffer.put(message);
            }

            private void processWholeBinary(byte[] bytes) {
                String stringBytes = new String(bytes, StandardCharsets.UTF_8);
                System.out.println("processWholeBinary: " + stringBytes);
                collectedBytes.add(bytes);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket,
                                              int statusCode,
                                              String reason) {
                actual.complete(collectedBytes);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                actual.completeExceptionally(error);
            }
        };

        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), listener)
                .join();

        List<byte[]> a = actual.join();
        assertEquals(a, expected);
    }

    @Test
    public void simpleAggregatingTextMessages() throws IOException {

        List<String> expected = List.of("alpha", "beta", "gamma", "delta");

        int[] binary = new int[]{
                0x81, 0x05, 0x61, 0x6c, 0x70, 0x68, 0x61, // "alpha"
                0x01, 0x02, 0x62, 0x65,                   // "be
                0x80, 0x02, 0x74, 0x61,                   // ta"
                0x01, 0x01, 0x67,                         // "g
                0x00, 0x01, 0x61,                         // a
                0x00, 0x00,                               //
                0x00, 0x00,                               //
                0x00, 0x01, 0x6d,                         // m
                0x00, 0x01, 0x6d,                         // m
                0x80, 0x01, 0x61,                         // a"
                0x8a, 0x00,                               // <PONG>
                0x01, 0x04, 0x64, 0x65, 0x6c, 0x74,       // "delt
                0x00, 0x01, 0x61,                         // a
                0x80, 0x00,                               // "
                0x88, 0x00                                // <CLOSE>
        };
        CompletableFuture<List<String>> actual = new CompletableFuture<>();

        server = Support.serverWithCannedData(binary);
        server.open();

        WebSocket.Listener listener = new WebSocket.Listener() {

            List<String> collectedStrings = new ArrayList<>();
            StringBuilder text = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket,
                                             CharSequence message,
                                             boolean last) {
                System.out.printf("onText(%s, %s)%n", message, last);
                webSocket.request(1);
                text.append(message);
                if (last) {
                    String str = text.toString();
                    text.setLength(0);
                    processWholeText(str);
                }
                return null;
            }

            private void processWholeText(String string) {
                System.out.println(string);
                collectedStrings.add(string);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket,
                                              int statusCode,
                                              String reason) {
                actual.complete(collectedStrings);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                actual.completeExceptionally(error);
            }
        };

        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), listener)
                .join();

        List<String> a = actual.join();
        assertEquals(a, expected);
    }

    /*
     * Exercises the scenario where requests for more messages are made prior to
     * completing the returned CompletionStage instances.
     */
    @Test
    public void aggregatingTextMessages() throws IOException {

        List<String> expected = List.of("alpha", "beta", "gamma", "delta");

        int[] binary = new int[]{
                0x81, 0x05, 0x61, 0x6c, 0x70, 0x68, 0x61, // "alpha"
                0x01, 0x02, 0x62, 0x65,                   // "be
                0x80, 0x02, 0x74, 0x61,                   // ta"
                0x01, 0x01, 0x67,                         // "g
                0x00, 0x01, 0x61,                         // a
                0x00, 0x00,                               //
                0x00, 0x00,                               //
                0x00, 0x01, 0x6d,                         // m
                0x00, 0x01, 0x6d,                         // m
                0x80, 0x01, 0x61,                         // a"
                0x8a, 0x00,                               // <PONG>
                0x01, 0x04, 0x64, 0x65, 0x6c, 0x74,       // "delt
                0x00, 0x01, 0x61,                         // a
                0x80, 0x00,                               // "
                0x88, 0x00                                // <CLOSE>
        };
        CompletableFuture<List<String>> actual = new CompletableFuture<>();


        server = Support.serverWithCannedData(binary);
        server.open();

        WebSocket.Listener listener = new WebSocket.Listener() {

            List<CharSequence> parts = new ArrayList<>();
            /*
             * A CompletableFuture which will complete once the current
             * message has been fully assembled. Until then the listener
             * returns this instance for every call.
             */
            CompletableFuture<?> currentCf = new CompletableFuture<>();
            List<String> collected = new ArrayList<>();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket,
                                             CharSequence message,
                                             boolean last) {
                parts.add(message);
                if (!last) {
                    webSocket.request(1);
                } else {
                    this.currentCf.thenRun(() -> webSocket.request(1));
                    CompletableFuture<?> refCf = this.currentCf;
                    processWholeMessage(new ArrayList<>(parts), refCf);
                    currentCf = new CompletableFuture<>();
                    parts.clear();
                    return refCf;
                }
                return currentCf;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket,
                                              int statusCode,
                                              String reason) {
                actual.complete(collected);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                actual.completeExceptionally(error);
            }

            public void processWholeMessage(List<CharSequence> data,
                                            CompletableFuture<?> cf) {
                StringBuilder b = new StringBuilder();
                data.forEach(b::append);
                String s = b.toString();
                System.out.println(s);
                cf.complete(null);
                collected.add(s);
            }
        };

        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), listener)
                .join();

        List<String> a = actual.join();
        assertEquals(a, expected);
    }
}
