package Shared;

import common.Common;
import gscode.GsCode;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.compression.ZlibEncoder;

import java.util.zip.Deflater;


public class Package {
	private static final int COMPRESS_SIZE = 204800000; // disable it until client support this
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
		if(length > COMPRESS_SIZE) {
			buf.writeShortLE(opcode);
			buf.writeBytes(body);
			Deflater compresser = new Deflater();
			// copy the data due to buf is direct buffer which is reference counted, it don't support array() method
			byte[] data = new byte[buf.readableBytes()];
			buf.getBytes(0, data);
			compresser.setInput(data);
			compresser.finish();
			byte[] out = new byte[data.length];
			int compressedDataLength = compresser.deflate(out);
			compresser.end();
			buf.clear();
			buf.writeIntLE(compressedDataLength);
			buf.writeShortLE(Common.OpCode.compressed_VALUE);
			buf.writeBytes(out, 0, compressedDataLength);
		}
		else {
			buf.writeIntLE(length);
			buf.writeShortLE(opcode);
			if (body != null)
				buf.writeBytes(body);
		}
	}
	public short opcode;
	public byte[] body;
}
