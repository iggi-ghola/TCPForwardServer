package com.kozhekin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

class SharedData {
    final ByteBuffer buffer;
    final SelectionKey pairedKey;

    SharedData(final ByteBuffer buffer, final SelectionKey pairedKey) {
        this.buffer = buffer;
        this.pairedKey = pairedKey;
    }

    ByteBuffer getBuffer() {
        return buffer;
    }

    SelectionKey getPairedKey() {
        return pairedKey;
    }
}
