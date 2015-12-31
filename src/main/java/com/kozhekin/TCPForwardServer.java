package com.kozhekin;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.concurrent.Callable;

public class TCPForwardServer implements Callable {
    private final String targetHost;
    private final int targetPort;
    private final int serverPort;
    private static final int BUFF_SIZE = 8192;

    public TCPForwardServer(final String targetHost, final int targetPort, final int serverPort) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.serverPort = serverPort;
    }

    @Override
    public Object call() throws Exception {
        final ServerSocketChannel serverChannel = initServerChannel();
        final Selector selector = initSelector(serverChannel);
        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                selector.select();

                final Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    final SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        accept(key);
                    } else if (key.isConnectable()) {
                        connect(key);
                    } else if (key.isReadable()) {
                        read(key);
                    } else if (key.isWritable()) {
                        write(key);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void accept(final SelectionKey key) throws IOException {
        final ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        final SocketChannel sourceChannel = serverSocketChannel.accept();
        sourceChannel.configureBlocking(false);
        final SelectionKey sourceKey = sourceChannel.register(key.selector(), 0);

        final SocketChannel targetChannel = SocketChannel.open();
        targetChannel.configureBlocking(false);
        targetChannel.connect(new InetSocketAddress(targetHost, targetPort));
        // At this step there is no data to transfer, so we clear all interest Ops
        final SelectionKey targetKey = targetChannel.register(key.selector(), SelectionKey.OP_CONNECT);

        sourceKey.attach(new SharedData(ByteBuffer.allocate(BUFF_SIZE), targetKey));
        targetKey.attach(new SharedData(ByteBuffer.allocate(BUFF_SIZE), sourceKey));
    }

    private void connect(final SelectionKey key) {
        final SelectableChannel channel = key.channel();
        final SharedData sharedData = getSharedData(key);
        if (channel instanceof SocketChannel) {
            try {
                if (((SocketChannel) channel).finishConnect()) {
                    key.interestOps(SelectionKey.OP_READ);
                    sharedData.getPairedKey().interestOps(SelectionKey.OP_READ);
                }
            } catch (IOException e) {
                closeAndCancel(key, sharedData.getPairedKey(), channel, sharedData.getPairedKey().channel());
            }
        }
    }

    private void read(final SelectionKey key) {
        final SocketChannel socketChannel = (SocketChannel) key.channel();

        final SharedData sharedData = getSharedData(key);
        final ByteBuffer buffer = sharedData.getBuffer();
        buffer.clear();
        final SelectionKey pairedKey = sharedData.getPairedKey();

        int numRead;
        try {
            numRead = socketChannel.read(buffer);
        } catch (IOException e) {
            closeAndCancel(key, pairedKey, socketChannel, pairedKey.channel());
            return;
        }
        if (numRead == -1) {
            // Remote entity shut the socket down cleanly. Do the
            // same from our end and cancel the channel.
            closeAndCancel(key, pairedKey, socketChannel, pairedKey.channel());
            return;
        }
        //noinspection MagicConstant
        key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
        pairedKey.interestOps(pairedKey.interestOps() | SelectionKey.OP_WRITE);

    }

    private void write(final SelectionKey key) {
        final SocketChannel socketChannel = (SocketChannel) key.channel();

        final SharedData sharedData = getSharedData(key);
        final SelectionKey pairedKey = sharedData.getPairedKey();
        final ByteBuffer buffer = getSharedData(pairedKey).getBuffer();
        buffer.flip();

        try {
            socketChannel.write(buffer);
        } catch (IOException e) {
            closeAndCancel(key, pairedKey, socketChannel, pairedKey.channel());
            return;
        }
        if (0 == buffer.remaining()) {
            //noinspection MagicConstant
            key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
            pairedKey.interestOps(key.interestOps() | SelectionKey.OP_READ);
        }
    }

    private ServerSocketChannel initServerChannel() throws IOException {
        final ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(serverPort));
        return serverChannel;
    }

    private Selector initSelector(final ServerSocketChannel channel) throws IOException {
        Selector socketSelector = SelectorProvider.provider().openSelector();
        channel.register(socketSelector, SelectionKey.OP_ACCEPT);
        return socketSelector;
    }

    private SharedData getSharedData(final SelectionKey key) {
        return (SharedData) key.attachment();
    }


    private void closeAndCancel(final SelectionKey key1, final SelectionKey key2, final SelectableChannel channel1, final SelectableChannel channel2) {
        closeSilently(channel1);
        closeSilently(channel2);
        key1.cancel();
        key2.cancel();
    }

    private void closeSilently(final Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }

}
