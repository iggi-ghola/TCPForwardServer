package com.kozhekin;

import org.junit.Test;

import java.util.concurrent.*;

import static org.junit.As  sert.*;

public class TCPForwardServerTest {

    @Test
    public void test() throws InterruptedException, ExecutionException, TimeoutException {
        final TCPForwardServer server = new TCPForwardServer("google.com", 80, 8888);
        ExecutorService exec = Executors.newFixedThreadPool(1);
        exec.submit(server).get(60, TimeUnit.SECONDS);
        exec.shutdown();
    }
}