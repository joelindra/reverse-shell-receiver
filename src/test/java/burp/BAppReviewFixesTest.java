package burp;

import burp.listener.SessionManager;
import burp.listener.ShellSession;
import burp.ui.AddressChip;
import burp.ui.ModernButton;
import burp.ui.UITheme;
import org.junit.Test;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Unit tests verifying all 5 PortSwigger BApp Store submission review fixes:
 * 1. Consistent table interaction without disruptive resets during streaming output.
 * 2. Instant address chip copying via mousePressed and theme adaptation.
 * 3. Uniform cell borders and focus suppression on session tables.
 * 4. Full Dark Mode support across UITheme, ModernButton, and AddressChip.
 * 5. Frozen uptime calculation on closed reverse shell sessions.
 */
public class BAppReviewFixesTest {

    // =========================================================================
    // FEEDBACK #5: UPTIME FREEZE ON CLOSED SESSIONS
    // =========================================================================
    @Test
    public void testUptimeFreezesOnClosedSession() throws Exception {
        DummySocket socket = new DummySocket();
        ShellSession session = new ShellSession(101, socket, createMockCallbacks(), null);

        assertTrue("Session should be active initially", session.isActive());
        String initialUptime = session.getUptime();
        assertNotNull(initialUptime);

        // Wait 1.1 seconds so uptime increments by at least 1 second
        Thread.sleep(1100);
        String activeUptime = session.getUptime();
        assertNotNull(activeUptime);

        // Close the session to freeze uptime
        session.close();
        assertFalse("Session should be inactive after close", session.isActive());
        String frozenUptime1 = session.getUptime();
        assertNotNull(frozenUptime1);

        // Wait another 1.1 seconds and verify uptime does NOT change
        Thread.sleep(1100);
        String frozenUptime2 = session.getUptime();

        assertEquals("Uptime must remain completely frozen after session is closed", frozenUptime1, frozenUptime2);

        // Subsequent calls to close() must not alter or unfreeze uptime
        session.close();
        String frozenUptime3 = session.getUptime();
        assertEquals("Subsequent close() calls must not change frozen uptime", frozenUptime1, frozenUptime3);
    }

    // =========================================================================
    // FEEDBACK #2: ADDRESS CHIP RELIABLE ONE-CLICK COPY & THEME
    // =========================================================================
    @Test
    public void testAddressChipMousePressedCopy() {
        AddressChip chip = new AddressChip("192.168.1.100:4444", "192.168.1.100:4444");

        // Verify listeners are registered
        assertTrue("Chip must have at least one mouse listener", chip.getMouseListeners().length > 0);

        // Simulate mousePressed left-click
        MouseEvent pressEvent = new MouseEvent(
                chip,
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                10,
                10,
                1,
                false,
                MouseEvent.BUTTON1
        );

        // Dispatching mousePressed should not throw any exception
        for (java.awt.event.MouseListener ml : chip.getMouseListeners()) {
            ml.mousePressed(pressEvent);
        }

        // Verify chip painting in both normal and copied state
        chip.setSize(120, 26);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(120, 26, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        chip.paint(g2);
        g2.dispose();

        // Verify chip label updated to copied text even in headless environments
        assertEquals("Label should indicate copied status", "COPIED: 192.168.1.100:4444", ((JLabel) chip.getComponent(0)).getText());
    }

    // =========================================================================
    // FEEDBACK #3: CONSISTENT TABLE CELL BORDERS AND FOCUS SUPPRESSION
    // =========================================================================
    @Test
    public void testTableCellRendererBordersAndFocusSuppression() {
        String[] cols = { "ID", "Host", "Port", "Status" };
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[] { 1, "127.0.0.1", 4444, "ACTIVE" });

        JTable table = new JTable(model);
        UITheme.styleTable(table);

        assertEquals("Row height should be 24px", 24, table.getRowHeight());
        assertTrue("Table must show horizontal lines", table.getShowHorizontalLines());
        assertTrue("Table must show vertical lines", table.getShowVerticalLines());
        assertEquals("Intercell spacing must be 1x1", new Dimension(1, 1), table.getIntercellSpacing());

        // Verify custom cell renderer on all columns
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableCellRenderer renderer = table.getColumnModel().getColumn(i).getCellRenderer();
            assertNotNull("Each column must have a cell renderer", renderer);

            // Test component rendering with hasFocus = true
            Component c = renderer.getTableCellRendererComponent(table, model.getValueAt(0, i), true, true, 0, i);
            assertTrue("Renderer must return a JComponent", c instanceof JComponent);
            JComponent jc = (JComponent) c;

            // Border must be an EmptyBorder(3, 8, 3, 8), NOT Swing's default single-cell line focus border
            assertTrue("Cell border must be EmptyBorder for consistent multi-column appearance",
                    jc.getBorder() instanceof EmptyBorder);
            Insets insets = jc.getBorder().getBorderInsets(jc);
            assertEquals(3, insets.top);
            assertEquals(8, insets.left);
            assertEquals(3, insets.bottom);
            assertEquals(8, insets.right);
        }
    }

    // =========================================================================
    // FEEDBACK #4: DARK MODE ADAPTIVE PALETTE & MODERN BUTTON
    // =========================================================================
    @Test
    public void testThemeColorsAndModernButtonDisabledContrast() {
        assertNotNull(UITheme.getPrimaryBg());
        assertNotNull(UITheme.getCardBg());
        assertNotNull(UITheme.getCardHeaderBg());
        assertNotNull(UITheme.getBorderColor());
        assertNotNull(UITheme.getTextPrimary());
        assertNotNull(UITheme.getTextSecondary());
        assertNotNull(UITheme.getTextMuted());
        assertNotNull(UITheme.getDisabledButtonBg());
        assertNotNull(UITheme.getDisabledButtonFg());
        assertNotNull(UITheme.getTableGridColor());

        // Test ModernButton rendering in enabled and disabled states
        ModernButton btn = new ModernButton("TEST BUTTON", UITheme.STATUS_INFO);
        btn.setSize(100, 30);

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(100, 30, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        // 1. Enabled rendering
        btn.paint(g2);

        // 2. Disabled rendering: must use adaptive disabled colors, never blinding white
        btn.setEnabled(false);
        btn.paint(g2);
        g2.dispose();

        // Verify disabled foreground is set to theme-aware disabled color
        assertEquals(UITheme.getDisabledButtonFg(), btn.getForeground());
    }

    // =========================================================================
    // FEEDBACK #1: NON-DISRUPTIVE SESSION STREAMING & STATE NOTIFICATION
    // =========================================================================
    @Test
    public void testSessionManagerOutputDoesNotTriggerDisruptiveTableRebuild() throws Exception {
        SessionManager manager = new SessionManager(Executors.newCachedThreadPool());
        AtomicInteger stateChangeCount = new AtomicInteger(0);

        manager.setListener(new SessionManager.ManagerListener() {
            @Override public void onSessionAdded(ShellSession session) {}
            @Override public void onSessionSelected(ShellSession session) {}
            @Override public void onSessionRemoved(ShellSession session) {}
            @Override public void onSessionStateChanged() {
                stateChangeCount.incrementAndGet();
            }
        });

        DummySocket socket = new DummySocket();
        ShellSession s1 = manager.registerNewSession(socket);
        assertEquals(0, stateChangeCount.get());

        // Simulate incoming terminal output by writing to the socket
        socket.feedInput("uid=0(root) gid=0(root) groups=0(root)\n");

        // Wait briefly for reader thread to process
        Thread.sleep(200);

        // Crucial test: terminal output must NOT trigger onSessionStateChanged!
        assertEquals("Output streaming must not trigger onSessionStateChanged to prevent table view flickering/unresponsive clicks",
                0, stateChangeCount.get());

        // Terminating the session via SessionManager SHOULD trigger onSessionStateChanged
        manager.closeSession(s1.getId());
        assertTrue("Closing session via manager should trigger state changed callback", stateChangeCount.get() >= 1);
    }

    @Test
    public void testSessionManagerSelectSession() throws Exception {
        SessionManager manager = new SessionManager(Executors.newCachedThreadPool());
        DummySocket sock1 = new DummySocket();
        DummySocket sock2 = new DummySocket();

        ShellSession s1 = manager.registerNewSession(sock1);
        ShellSession s2 = manager.registerNewSession(sock2);

        // Newly registered session becomes active
        assertEquals(s2.getId(), manager.getActiveSession().getId());

        // Switch to session 1
        manager.selectSessionById(s1.getId());
        assertEquals(s1.getId(), manager.getActiveSession().getId());

        // Switch back to session 2
        manager.selectSessionById(s2.getId());
        assertEquals(s2.getId(), manager.getActiveSession().getId());
    }

    @Test
    public void testStreamingPromptWithoutNewline() throws Exception {
        DummySocket socket = new DummySocket();
        java.util.List<String> receivedChunks = new java.util.concurrent.CopyOnWriteArrayList<>();
        ShellSession.SessionEventListener listener = new ShellSession.SessionEventListener() {
            @Override public void onSessionOutput(ShellSession session, String text) {
                receivedChunks.add(text);
            }
            @Override public void onSessionTerminated(ShellSession session) {}
            @Override public void onPathUpdated(ShellSession session, String newPath) {}
        };

        ShellSession session = new ShellSession(999, socket, createMockCallbacks(), listener);
        java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
        session.startReading(executor);

        // Feed prompt without newline (e.g. interactive shell prompt)
        socket.feedInput("root@server:~# ");

        // Wait up to 1000ms
        long start = System.currentTimeMillis();
        while (receivedChunks.isEmpty() && System.currentTimeMillis() - start < 1000) {
            Thread.sleep(50);
        }

        assertFalse("Interactive prompt without trailing newline must be received and displayed immediately", receivedChunks.isEmpty());
        assertTrue("Output should contain the streamed prompt", receivedChunks.get(0).contains("root@server:~# "));

        // Send a clean command and verify output written to socket stream
        session.sendCommand("id", executor);
        Thread.sleep(200);

        String sentData = socket.getOutputStream().toString();
        assertTrue("Sent command should contain 'id' followed by newline", sentData.contains("id\r\n") || sentData.contains("id\n"));
        assertFalse("Command must not append PWD markers directly to command string", sentData.contains("___PWD_START___"));

        session.close();
        executor.shutdownNow();
    }

    // =========================================================================
    // TERMINAL SANITIZATION & DEDUPLICATION TESTS
    // =========================================================================
    @Test
    public void testTerminalSanitizationRemovesOscAndTrash() {
        // 1. Standard OSC window title sequence with BEL
        String oscWithBel = "\u001B]0;root@DESKTOP-6ALMHTS: ~\u0007┌──(root㉿DESKTOP-6ALMHTS)-[~]\n└─# ";
        String cleaned1 = ShellSession.sanitizeTerminalOutput(oscWithBel);
        assertFalse("Cleaned output must not contain OSC title sequence", cleaned1.contains("]0;root@"));
        assertTrue("Cleaned output must preserve the actual prompt header", cleaned1.contains("┌──(root㉿DESKTOP-6ALMHTS)-[~]"));
        assertTrue("Cleaned output must preserve the actual prompt prompt character", cleaned1.contains("└─#"));

        // 2. Bare OSC window title sequence (when ESC was dropped or unrendered)
        String bareOsc = "]0;root@DESKTOP-6ALMHTS: ~┌──(root㉿DESKTOP-6ALMHTS)-[~]\n└─# ";
        String cleaned2 = ShellSession.sanitizeTerminalOutput(bareOsc);
        assertFalse("Cleaned output must strip bare ]0; sequence", cleaned2.contains("]0;root@"));
        assertTrue("Cleaned output must preserve prompt", cleaned2.contains("┌──(root㉿DESKTOP-6ALMHTS)-[~]"));

        // 3. ANSI CSI colors and bracketed paste modes
        String csiInput = "\u001B[?2004h\u001B[01;32mroot\u001B[00m@\u001B[01;34mserver\u001B[00m:# \u001B[?2004l";
        String cleanedCsi = ShellSession.sanitizeTerminalOutput(csiInput);
        assertEquals("root@server:#", cleanedCsi.trim());

        // 4. Carriage return overwrite emulation
        String crInput = "downloading 10%\rdownloading 50%\rdownloading 100%\n";
        String cleanedCr = ShellSession.processCarriageReturns(crInput);
        assertEquals("downloading 100%\n", cleanedCr);

        // 5. Readline prompt overwrite with carriage return
        String promptCr = "└─# \r└─# ";
        String cleanedPromptCr = ShellSession.processCarriageReturns(promptCr);
        assertEquals("└─# ", cleanedPromptCr);
    }

    @Test
    public void testPromptDeduplicationKali() {
        // User's exact reported raw artifact structure
        String rawKaliTrash =
                "]0;root@DESKTOP-6ALMHTS: ~┌──(root㉿DESKTOP-6ALMHTS)-[~]\n\n" +
                "└─# \n" +
                "└─# \n" +
                "]0;root@DESKTOP-6ALMHTS: ~┌──(root㉿DESKTOP-6ALMHTS)-[~]\n\n" +
                "└─# \n" +
                "└─# \n\n" +
                "]0;root@DESKTOP-6ALMHTS: ~┌──(root㉿DESKTOP-6ALMHTS)-[~]\n\n" +
                "└─# \n" +
                "└─# ";

        String cleaned = ShellSession.sanitizeTerminalOutput(rawKaliTrash);

        // Verify all ]0; title sequences are gone
        assertFalse("Must not contain any OSC ]0; trash", cleaned.contains("]0;"));

        // Count occurrences of ┌── and └─#
        int countTop = 0;
        int idx = 0;
        while ((idx = cleaned.indexOf("┌──", idx)) != -1) {
            countTop++;
            idx += 3;
        }

        int countBot = 0;
        idx = 0;
        while ((idx = cleaned.indexOf("└─#", idx)) != -1) {
            countBot++;
            idx += 3;
        }

        assertEquals("Multi-line prompt top header should appear exactly once after deduplication", 1, countTop);
        assertEquals("Prompt input symbol └─# should appear exactly once after deduplication", 1, countBot);
        assertFalse("Must not have 3 or more consecutive newlines", cleaned.contains("\n\n\n"));
    }

    @Test
    public void testDynamicThemeSwitching() {
        // Test listener registration and notification dispatch
        AtomicBoolean themeNotified = new AtomicBoolean(false);
        Runnable listener = () -> themeNotified.set(true);

        UITheme.addThemeChangeListener(listener);
        UITheme.fireThemeChanged();
        assertTrue("Theme change listener should be notified when fireThemeChanged() is invoked", themeNotified.get());
        UITheme.removeThemeChangeListener(listener);

        // Test component theme refresh
        burp.ui.AddressChip chip = new burp.ui.AddressChip("192.168.1.100:8080");
        chip.refreshTheme();
        chip.updateUI();
        assertNotNull(chip);

        burp.ui.ModernButton btn = new burp.ui.ModernButton("TEST", UITheme.STATUS_SUCCESS);
        btn.updateUI();
        assertNotNull(btn);
    }

    // =========================================================================
    // NEW BAPP REVIEW FIXES (7 ITEMS)
    // =========================================================================

    @Test
    public void testKillPortsExcludesBurpOwnPid() {
        int currentPid = burp.listener.PortScanner.getCurrentPid();
        assertTrue("Current JVM PID must be resolved (> 0)", currentPid > 0);

        java.util.List<burp.listener.PortScanner.PortInfo> rawList = new java.util.ArrayList<>();
        rawList.add(new burp.listener.PortScanner.PortInfo(8080, "TCP", "127.0.0.1", currentPid, "java"));
        rawList.add(new burp.listener.PortScanner.PortInfo(18080, "TCP", "0.0.0.0", currentPid, "java"));
        rawList.add(new burp.listener.PortScanner.PortInfo(9090, "TCP", "0.0.0.0", currentPid + 99999, "nc"));

        java.util.List<burp.listener.PortScanner.PortInfo> filtered = burp.listener.PortScanner.excludePid(rawList, currentPid);
        assertEquals("Both Burp ports must be excluded", 1, filtered.size());
        assertEquals(9090, filtered.get(0).getPort());

        assertFalse("killProcess must refuse to kill Burp's own PID", burp.listener.PortScanner.killProcess(currentPid));
    }

    @Test
    public void testOversizedRequestLineAndHeadersReturn431WithoutOom() throws Exception {
        ReverseShellReceiverPanel panel = new ReverseShellReceiverPanel(createFullMockCallbacks(new HashMap<>()));

        // 1. Send > 8 KB single line with no newline
        byte[] oversizedLine = new byte[ReverseShellReceiverPanel.MAX_HEADER_LINE_BYTES + 512];
        java.util.Arrays.fill(oversizedLine, (byte) 'A');

        BoundedTestSocket sock1 = new BoundedTestSocket(oversizedLine, 18080);
        panel.handleWebhookRequest(sock1, 18080, false);

        String resp1 = new String(sock1.getWrittenBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("Oversized request line must return 431", resp1.startsWith("HTTP/1.1 431"));

        // 2. Send > 64 KB total headers across multiple valid-length lines
        StringBuilder manyHeaders = new StringBuilder("GET /test HTTP/1.1\r\n");
        String singleHeader = "X-Pad-Header: " + new String(new char[1000]).replace('\0', 'B') + "\r\n";
        for (int i = 0; i < 70; i++) {
            manyHeaders.append(singleHeader);
        }
        manyHeaders.append("\r\n");

        BoundedTestSocket sock2 = new BoundedTestSocket(manyHeaders.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), 18080);
        panel.handleWebhookRequest(sock2, 18080, false);

        String resp2 = new String(sock2.getWrittenBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("Oversized total headers must return 431", resp2.startsWith("HTTP/1.1 431"));
        panel.cleanup();
    }

    @Test
    public void testIdleConnectionDoesNotBlockConcurrentWebhookRequests() throws Exception {
        ReverseShellReceiverPanel panel = new ReverseShellReceiverPanel(createFullMockCallbacks(new HashMap<>()));

        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            java.util.concurrent.ExecutorService pool = Executors.newCachedThreadPool();
            AtomicBoolean running = new AtomicBoolean(true);

            Thread acceptThread = new Thread(() -> {
                while (running.get()) {
                    try {
                        Socket client = server.accept();
                        client.setSoTimeout(ReverseShellReceiverPanel.WEBHOOK_SO_TIMEOUT_MS);
                        pool.submit(() -> {
                            try (Socket c = client) {
                                panel.handleWebhookRequest(c, port, false);
                            } catch (Throwable ignored) {}
                        });
                    } catch (Exception e) {
                        break;
                    }
                }
            });
            acceptThread.start();

            // Client 1 connects and sends nothing (idle)
            try (Socket idleClient = new Socket("127.0.0.1", port);
                 Socket activeClient = new Socket("127.0.0.1", port)) {
                activeClient.setSoTimeout(2000);
                long start = System.currentTimeMillis();
                activeClient.getOutputStream().write("GET /c HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                activeClient.getOutputStream().flush();

                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(activeClient.getInputStream()));
                String statusLine = reader.readLine();
                long elapsed = System.currentTimeMillis() - start;

                assertNotNull("Active client must receive a response while idle client is connected", statusLine);
                assertTrue("Response must be 200 OK", statusLine.contains("200 OK"));
                assertTrue("Response must arrive quickly (< 1500ms) despite idle client", elapsed < 1500);
            } finally {
                running.set(false);
                pool.shutdownNow();
            }
        } finally {
            panel.cleanup();
        }
    }

    @Test
    public void testMockRoutesSyncOnAddDeleteAndToggle() {
        ReverseShellReceiverPanel panel = new ReverseShellReceiverPanel(createFullMockCallbacks(new HashMap<>()));
        burp.listener.WebhookResponseConfig config = panel.getWebhookConfig();
        DefaultTableModel model = new DefaultTableModel(new String[] { "Enabled", "Path", "Match Type", "Status", "Content-Type", "Description" }, 0);

        panel.reloadMockRoutesTableModel(model);
        assertEquals(4, model.getRowCount());

        // 1. Add /test-new and verify it is served, then delete it and verify it is no longer served
        config.removeMockRouteByPath("/test-new");
        config.addMockRoute(new burp.listener.MockRoute("/test-new", burp.listener.MockRoute.MatchType.EXACT, "200 OK", "application/json", "{\"status\":\"ok\"}", "", "Custom"));
        panel.reloadMockRoutesTableModel(model);
        assertEquals(5, model.getRowCount());
        assertNotNull(config.findMatchingRoute("/test-new"));

        // Delete selected row for /test-new (row 4)
        String pathToRemove = String.valueOf(model.getValueAt(4, 1));
        assertEquals("/test-new", pathToRemove);
        config.removeMockRouteByPath(pathToRemove);
        panel.reloadMockRoutesTableModel(model);
        assertEquals(4, model.getRowCount());
        assertNull("Deleted route /test-new must no longer be served", config.findMatchingRoute("/test-new"));

        // 2. Delete row 0 twice (/redirect, then /aws-meta)
        String firstRowPath1 = String.valueOf(model.getValueAt(0, 1));
        assertEquals("/redirect", firstRowPath1);
        config.removeMockRouteByPath(firstRowPath1);
        panel.reloadMockRoutesTableModel(model);

        String firstRowPath2 = String.valueOf(model.getValueAt(0, 1));
        assertEquals("/aws-meta", firstRowPath2);
        config.removeMockRouteByPath(firstRowPath2);
        panel.reloadMockRoutesTableModel(model);

        assertEquals(2, model.getRowCount());
        assertNull("/redirect must be gone", config.findMatchingRoute("/redirect"));
        assertNull("/aws-meta must be gone", config.findMatchingRoute("/aws-meta"));

        // 3. Toggle Enabled on the new row 0 (/payload)
        String currentRow0Path = String.valueOf(model.getValueAt(0, 1));
        assertEquals("/payload", currentRow0Path);
        burp.listener.MockRoute payloadRoute = config.findRouteByPath(currentRow0Path);
        assertNotNull(payloadRoute);
        payloadRoute.setEnabled(false);
        panel.reloadMockRoutesTableModel(model);
        assertNull("Disabled route /payload must not match requests", config.findMatchingRoute("/payload"));
        panel.cleanup();
    }

    @Test
    public void testHeaderInjectionStrippedAndStatusCodeValidated() {
        burp.listener.WebhookResponseConfig config = new burp.listener.WebhookResponseConfig();

        // 1. Header injection via ?status=
        byte[] resp1 = config.buildResponseBytes("127.0.0.1", "GET", "/?status=200%0d%0aX-Injected:%20pwned", 8080, "Host: localhost", 0);
        String str1 = new String(resp1, java.nio.charset.StandardCharsets.UTF_8);
        assertFalse("Must not allow header injection via status parameter", str1.contains("\r\nX-Injected:"));
        assertTrue("Invalid status with CRLF must fall back to 200 OK", str1.startsWith("HTTP/1.1 200 OK\r\n"));

        // 2. Out-of-range or non-numeric ?status=
        byte[] resp2 = config.buildResponseBytes("127.0.0.1", "GET", "/?status=999", 8080, "Host: localhost", 0);
        String str2 = new String(resp2, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("Out-of-range status 999 must fall back to 200 OK", str2.startsWith("HTTP/1.1 200 OK\r\n"));

        byte[] resp3 = config.buildResponseBytes("127.0.0.1", "GET", "/?status=invalid", 8080, "Host: localhost", 0);
        String str3 = new String(resp3, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("Non-numeric status must fall back to 200 OK", str3.startsWith("HTTP/1.1 200 OK\r\n"));

        // 3. Header injection via ?type=
        byte[] resp4 = config.buildResponseBytes("127.0.0.1", "GET", "/?status=200&type=text/plain%0d%0aX-Injected:%20pwned", 8080, "Host: localhost", 0);
        String str4 = new String(resp4, java.nio.charset.StandardCharsets.UTF_8);
        assertFalse("Must not allow header injection via type parameter", str4.contains("\r\nX-Injected:"));
        assertTrue("CRLF must be stripped from Content-Type header", str4.contains("Content-Type: text/plainX-Injected: pwned\r\n"));
    }

    @Test
    public void testSendToRepeaterUsesActualConnectionTargetNotHostHeader() throws Exception {
        ReverseShellReceiverPanel panel = new ReverseShellReceiverPanel(createFullMockCallbacks(new HashMap<>()));

        String rawReq = "GET /callback HTTP/1.1\r\nHost: spoofed-attacker.example.com:9999\r\nUser-Agent: test\r\n\r\n";
        BoundedTestSocket sock = new BoundedTestSocket(rawReq.getBytes(java.nio.charset.StandardCharsets.UTF_8), 18080);
        panel.handleWebhookRequest(sock, 18080, false);

        // Flush SwingUtilities.invokeLater queue
        SwingUtilities.invokeAndWait(() -> {});

        IHttpService service = panel.getLatestWebhookHttpService();
        assertNotNull("Webhook entry must record IHttpService", service);
        assertEquals("Target host must be the actual connection address, not the Host header", "127.0.0.1", service.getHost());
        assertEquals("Target port must be the actual connection port, not the Host header port", 18080, service.getPort());
        assertEquals("http", service.getProtocol());
        panel.cleanup();
    }

    @Test
    public void testAllListenerSettingsPersistedAndLoaded() {
        java.util.Map<String, String> store = new HashMap<>();
        ReverseShellReceiverPanel panel1 = new ReverseShellReceiverPanel(createFullMockCallbacks(store));

        burp.listener.WebhookResponseConfig cfg1 = panel1.getWebhookConfig();
        cfg1.setStatusCode("201 Created");
        cfg1.setContentType("application/json; charset=utf-8");
        cfg1.setEnableCors(false);
        cfg1.setRedirectLocation("https://internal.corp/login");
        cfg1.setResponseBody("{\"custom\":true}");
        cfg1.removeMockRouteByPath("/aws-meta");
        cfg1.addMockRoute(new burp.listener.MockRoute("/persisted-route", burp.listener.MockRoute.MatchType.PREFIX, "200 OK", "text/plain", "hello", "", "Persisted"));
        cfg1.findRouteByPath("/redirect").setEnabled(false);

        panel1.saveSettings(false);

        ReverseShellReceiverPanel panel2 = new ReverseShellReceiverPanel(createFullMockCallbacks(store));
        panel2.loadSettings();

        burp.listener.WebhookResponseConfig cfg2 = panel2.getWebhookConfig();
        assertEquals("201 Created", cfg2.getStatusCode());
        assertEquals("application/json; charset=utf-8", cfg2.getContentType());
        assertFalse(cfg2.isEnableCors());
        assertEquals("https://internal.corp/login", cfg2.getRedirectLocation());
        assertEquals("{\"custom\":true}", cfg2.getResponseBody());
        assertNull("Deleted route /aws-meta must stay deleted after reload", cfg2.findRouteByPath("/aws-meta"));
        assertNotNull("Added route /persisted-route must be restored after reload", cfg2.findRouteByPath("/persisted-route"));
        assertFalse("Disabled state on /redirect must be preserved after reload", cfg2.findRouteByPath("/redirect").isEnabled());

        panel1.cleanup();
        panel2.cleanup();
    }

    private static IBurpExtenderCallbacks createFullMockCallbacks(java.util.Map<String, String> settings) {
        IExtensionHelpers helpers = (IExtensionHelpers) Proxy.newProxyInstance(
                IExtensionHelpers.class.getClassLoader(),
                new Class<?>[] { IExtensionHelpers.class },
                (p, m, a) -> null
        );
        IMessageEditor msgEditor = (IMessageEditor) Proxy.newProxyInstance(
                IMessageEditor.class.getClassLoader(),
                new Class<?>[] { IMessageEditor.class },
                (p, m, a) -> "getComponent".equals(m.getName()) ? new JPanel() : null
        );
        ITextEditor txtEditor = (ITextEditor) Proxy.newProxyInstance(
                ITextEditor.class.getClassLoader(),
                new Class<?>[] { ITextEditor.class },
                (p, m, a) -> {
                    if ("getComponent".equals(m.getName())) return new JPanel();
                    if ("getText".equals(m.getName())) return new byte[0];
                    return null;
                }
        );
        return (IBurpExtenderCallbacks) Proxy.newProxyInstance(
                IBurpExtenderCallbacks.class.getClassLoader(),
                new Class<?>[] { IBurpExtenderCallbacks.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getHelpers".equals(name)) return helpers;
                    if ("createMessageEditor".equals(name)) return msgEditor;
                    if ("createTextEditor".equals(name)) return txtEditor;
                    if ("loadExtensionSetting".equals(name)) return settings.get((String) args[0]);
                    if ("saveExtensionSetting".equals(name)) {
                        settings.put((String) args[0], (String) args[1]);
                        return null;
                    }
                    if ("getStderr".equals(name) || "getStdout".equals(name)) return new ByteArrayOutputStream();
                    return null;
                }
        );
    }

    private static class BoundedTestSocket extends Socket {
        private final ByteArrayInputStream in;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final int localPort;

        BoundedTestSocket(byte[] requestBytes, int localPort) {
            this.in = new ByteArrayInputStream(requestBytes);
            this.localPort = localPort;
        }

        @Override public InputStream getInputStream() { return in; }
        @Override public OutputStream getOutputStream() { return out; }
        @Override public InetAddress getInetAddress() {
            try { return InetAddress.getByName("127.0.0.1"); } catch (Exception e) { return null; }
        }
        @Override public InetAddress getLocalAddress() {
            try { return InetAddress.getByName("127.0.0.1"); } catch (Exception e) { return null; }
        }
        @Override public int getLocalPort() { return localPort; }
        byte[] getWrittenBytes() { return out.toByteArray(); }
    }

    // =========================================================================
    // MOCK HELPERS
    // =========================================================================

    private static IBurpExtenderCallbacks createMockCallbacks() {
        return (IBurpExtenderCallbacks) Proxy.newProxyInstance(
                IBurpExtenderCallbacks.class.getClassLoader(),
                new Class<?>[] { IBurpExtenderCallbacks.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("customizeUiComponent".equals(name)) {
                        return null;
                    }
                    if ("getStderr".equals(name) || "getStdout".equals(name)) {
                        return new ByteArrayOutputStream();
                    }
                    return null;
                }
        );
    }

    private static class DummySocket extends Socket {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private byte[] inputBytes = new byte[0];
        private volatile boolean closed = false;

        public DummySocket() {
        }

        public synchronized void feedInput(String data) {
            this.inputBytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getInputStream() {
            return new InputStream() {
                private int idx = 0;
                @Override
                public synchronized int read() {
                    while (!closed && idx >= inputBytes.length) {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            return -1;
                        }
                    }
                    if (closed || idx >= inputBytes.length) return -1;
                    return inputBytes[idx++] & 0xFF;
                }

                @Override
                public synchronized int read(byte[] b, int off, int len) {
                    while (!closed && idx >= inputBytes.length) {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            return -1;
                        }
                    }
                    if (closed || idx >= inputBytes.length) return -1;
                    int available = inputBytes.length - idx;
                    int toRead = Math.min(len, available);
                    System.arraycopy(inputBytes, idx, b, off, toRead);
                    idx += toRead;
                    return toRead;
                }
            };
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public InetAddress getInetAddress() {
            try {
                return InetAddress.getByName("127.0.0.1");
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public int getPort() {
            return 54321;
        }

        @Override
        public synchronized void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }
}
