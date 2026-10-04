class OptionalListIncomingFixture {
    Packet packet;
    void handle() {
        int itemId = this.packet.readInt();
        int amount = this.packet.readInt();
        boolean batch = this.packet.bytesAvailable() >= 4;
        if (batch) {
            int count = this.packet.readInt();
            if (count <= 0 || count > 10000) return;
            for (int i = 0; i < count; i++) { int id = this.packet.readInt(); }
        }
    }
}
