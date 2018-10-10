package Shared;

import common.Common;
import io.netty.buffer.ByteBuf;


public class Package {
	public static Package fail(short opcode, Common.Fail.Reason reason) {
		Common.Fail.Builder builder = Common.Fail.newBuilder();
		builder.setOpcode(opcode);
		builder.setReason(reason);
		Package pack = new Package(Common.OpCode.error_VALUE, builder.build());
		return pack;
	}
	public static Package fail(short opcode) {
		Common.Fail.Builder builder = Common.Fail.newBuilder();
		builder.setOpcode(opcode);
		Package pack = new Package(Common.OpCode.error_VALUE, builder.build());
		return pack;
	}
	public static Package create(int opcode, com.google.protobuf.Message message) {
		return new Package(opcode, message);
	}
//	public static Package create(int opcode, com.google.protobuf.GeneratedMessageV3 message) {
//		return new Package(opcode, message);
//	}

	public static Package create(int opcode) {
		return new Package(opcode);
	}

	// java enum value is int, so avoid cast, use int here
	private Package(int opcode, com.google.protobuf.Message message)  {
		this.opcode = (short) opcode;
		this.body = message.toByteArray();
	}

	private Package(int opcode) {
		this.opcode = (short) opcode;
		this.body = null;
	}
	
	public Package(ByteBuf buf) {
		this.opcode = buf.readShortLE();
		if(buf.readableBytes() > 0){
			this.body = new byte[buf.readableBytes()];
			buf.readBytes(this.body);
		}
	}
	public void toByteBuf(ByteBuf buf) {
		int length = (body == null) ? 0 : body.length;
		buf.writeIntLE(length);
		buf.writeShortLE(opcode);
		if(body != null)
			buf.writeBytes(body);
	}
	public short opcode;
	public byte[] body;
}
