class OptionalEffectIncomingFixture {
    Packet packet;
    void handle() {
        int effectId = this.packet.readInt();
        boolean keep = this.packet.bytesAvailable() >= 1 && this.packet.readBoolean();
        int selection = this.packet.bytesAvailable() >= 4 ? this.packet.readInt() : 0;
    }
}
