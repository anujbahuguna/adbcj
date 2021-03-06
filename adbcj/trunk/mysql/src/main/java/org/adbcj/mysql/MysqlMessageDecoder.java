/*
 *   Copyright (c) 2007 Mike Heath.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.adbcj.mysql;

import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.util.Set;

import org.apache.mina.common.IoBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.adbcj.DbException;
import org.adbcj.Value;
import org.adbcj.support.DefaultValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MysqlMessageDecoder extends CumulativeProtocolDecoder {
	private static final Logger logger = LoggerFactory.getLogger(MysqlMessageDecoder.class);

	private static final byte NULL_VALUE = (byte)0xfb;

	private static final byte RESPONSE_OK = 0x00;
	private static final byte RESPONSE_EOF = (byte)0xfe;
	private static final byte RESPONSE_ERROR = (byte)0xff;

	private static final int GREETING_UNUSED_SIZE = 13;
	private static final int SALT_SIZE = 8;
	private static final int SALT2_SIZE = 12;
	private static final int SQL_STATE_LENGTH = 5;

	private enum State {
		CONNECTING, RESPONSE, FIELD, FIELD_EOF, ROW
	}

	private State state = State.CONNECTING;
	private int fieldPacketCount = 0;
	private int fieldIndex = 0;
	private MysqlField[] fields;

	private final MysqlConnection connection;

	MysqlMessageDecoder(IoSession session) {
		connection = IoSessionUtil.getMysqlConnection(session);
	}

	@Override
	protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
		in.order(ByteOrder.LITTLE_ENDIAN);

		// Check to see if there's enough data in the buffer to read the packet length
		if (in.remaining() < 3) {
			return false;
		}
		
		// Read the packet length and determine if the buffer is big enough
		in.mark();
		final int length = in.getUnsignedMediumInt();
		// Make sure we have enough data for the packet length and the packet number
		if (in.remaining() < length + 1) {
			in.reset();
			return false;
		}
		final byte packetNumber = in.get();
		
		final int originalLimit = in.limit();
		try {
			in.limit(in.position() + length);
			
			logger.debug("Decoding in state {}", state);
			switch (state) {
			case CONNECTING:
				ServerGreeting serverGreeting = decodeServerGreeting(length, packetNumber, in);
				out.write(serverGreeting);
				state = State.RESPONSE;
				break;
			case RESPONSE:
				int fieldCount = in.get();
				if (fieldCount == RESPONSE_OK) {
					// Create Ok response
					OkResponse okResponse = decodeOkResponse(connection, in, length, packetNumber);
					out.write(okResponse);
				} else if (fieldCount == RESPONSE_ERROR) {
					// Create error response
					ErrorResponse response = decodeErrorResponse(in, length, packetNumber);
					out.write(response);
				} else if (fieldCount == RESPONSE_EOF) {
					throw new IllegalStateException("Did not expect an EOF response from the server");
				} else {
					// Must be receiving result set header
	
					// Rewind the buffer to read the binary length encoding
					in.position(in.position() - 1);
	
					// Get the number of fields. The largest this can be is a 24-bit
					// integer so cast to int is ok
					fieldPacketCount = (int)getBinaryLengthEncoding(in);
					fields = new MysqlField[fieldPacketCount];
					logger.trace("Field count {}", fieldPacketCount);
					
					Long extra = null;
					if (in.remaining() > 0) {
						extra = getBinaryLengthEncoding(in);
					}
	
					// Create result set response
					logger.debug("Sending result set response up filter chain");
					ResultSetResponse resultSetResponse = new ResultSetResponse(length, packetNumber,
							fieldPacketCount, extra);
					out.write(resultSetResponse);
	
					state = State.FIELD;
				}
				break;
			case FIELD:
				ResultSetFieldResponse resultSetFieldResponse = decodeFieldResponse(in, length, packetNumber);
				out.write(resultSetFieldResponse);
	
				fieldPacketCount--;
				logger.trace("fieldPacketCount: {}", fieldPacketCount);
				if (fieldPacketCount == 0) {
					state = State.FIELD_EOF;
				}
				break;
			case FIELD_EOF:
				EofResponse fieldEof = decodeEofResponse(in, length, packetNumber, EofResponse.Type.FIELD);
				out.write(fieldEof);
				out.flush();
	
				state = State.ROW;
				fieldIndex = 0;
				break;
			case ROW:
				fieldCount = in.get(); // This is only for checking for EOF
				in.position(in.position() - 1);
				if (fieldCount == RESPONSE_EOF) {
					EofResponse rowEof = decodeEofResponse(in, length, packetNumber, EofResponse.Type.ROW);
					out.write(rowEof);
					out.flush();
	
					state = State.RESPONSE;
	
					break;
				}
	
				Value[] values = new Value[fields.length];
				for (MysqlField field : fields) {
					Object value = null;
					if (in.get() != NULL_VALUE) {
						in.position(in.position() - 1);
						
						// We will have to move this as some datatypes will not be sent across the wire as strings
						String strVal = decodeLengthCodedString(in);
						
						switch (field.getColumnType()) {
						case TINYINT:
							value = Byte.valueOf(strVal);
							break;
						case INTEGER:
						case BIGINT:
							value = Long.valueOf(strVal);
							break;
						case VARCHAR:
							value = strVal;
							break;
						default:
							throw new IllegalStateException("Don't know how to handle column type of "
									+ field.getColumnType());
						}
					}
					values[field.getIndex()] = new DefaultValue(field, value);
				}
				out.write(new ResultSetRowResponse(length, packetNumber, values));
				break;
			default:
				throw new MysqlException(connection, "Unkown decoder state " + state);
			}
	
			if (in.hasRemaining()) {
				throw new IllegalStateException(String.format("Buffer has %d remaining bytes after decoding", in.remaining()));
			}
		} finally {
			in.limit(originalLimit);
		}
		return in.hasRemaining();
		
	}
	
	protected ErrorResponse decodeErrorResponse(IoBuffer buffer, int length, byte packetNumber)
			throws CharacterCodingException {
		int errorNumber = buffer.getUnsignedShort();
		buffer.get(); // Throw away sqlstate marker
		String sqlState = buffer.getString(SQL_STATE_LENGTH, MysqlCharacterSet.ASCII_BIN.getCharset().newDecoder());
		String message = buffer.getString(buffer.remaining(), connection.getCharacterSet().getCharset().newDecoder());
		return new ErrorResponse(length, packetNumber, errorNumber, sqlState, message);
	}

	protected ServerGreeting decodeServerGreeting(int length, byte packetNumber, IoBuffer buffer)
			throws CharacterCodingException {
		byte protocol = buffer.get();
		String version = buffer.getString(MysqlCharacterSet.ASCII_BIN.getCharset().newDecoder());
		int threadId = buffer.getInt();

		byte[] salt = new byte[SALT_SIZE + SALT2_SIZE];
		buffer.get(salt, 0, SALT_SIZE);
		buffer.get(); // Throw away 0 byte

		Set<ClientCapabilities> serverCapabilities = buffer.getEnumSetShort(ClientCapabilities.class);
		MysqlCharacterSet charSet = buffer.getEnum(MysqlCharacterSet.class);
		Set<ServerStatus> serverStatus = buffer.getEnumSetShort(ServerStatus.class);
		buffer.skip(GREETING_UNUSED_SIZE);

		buffer.get(salt, SALT_SIZE, SALT2_SIZE);
		buffer.get(); // Throw away 0 byte

		return new ServerGreeting(length, packetNumber, protocol, version, threadId, salt, serverCapabilities, charSet,
				serverStatus);
	}

	protected OkResponse decodeOkResponse(MysqlConnection connection, IoBuffer buffer, int length, byte packetNumber) throws CharacterCodingException {
		long affectedRows = getBinaryLengthEncoding(buffer);
		long insertId = 0;
		if (affectedRows > 0) {
			insertId = getBinaryLengthEncoding(buffer);
		}
		Set<ServerStatus> serverStatus = buffer.getEnumSetShort(ServerStatus.class);
		int warningCount = buffer.getUnsignedShort();
		String message = buffer.getString(buffer.remaining(), connection.getCharacterSet()
				.getCharset().newDecoder());

		return new OkResponse(length, packetNumber, affectedRows, insertId, serverStatus,
				warningCount, message);
	}

	protected ResultSetFieldResponse decodeFieldResponse(IoBuffer buffer,
			int packetLength, byte packetNumber) throws CharacterCodingException {
		String catalogName = decodeLengthCodedString(buffer);
		String schemaName = decodeLengthCodedString(buffer);
		String tableLabel = decodeLengthCodedString(buffer);
		String tableName = decodeLengthCodedString(buffer);
		String columnLabel = decodeLengthCodedString(buffer);
		String columnName = decodeLengthCodedString(buffer);
		buffer.get(); // Skip filler
		int characterSetNumber = buffer.getUnsignedShort();
		MysqlCharacterSet charSet = MysqlCharacterSet.findById(characterSetNumber);
		long length = buffer.getUnsignedInt();
		byte fieldTypeId = buffer.get();
		MysqlType fieldType = MysqlType.findById(fieldTypeId);
		Set<FieldFlag> flags = buffer.getEnumSet(FieldFlag.class);
		int decimals = buffer.getUnsigned();
		buffer.getShort(); // Skip filler
		long fieldDefault = getBinaryLengthEncoding(buffer);
		MysqlField field = new MysqlField(fieldIndex, catalogName, schemaName, tableLabel, tableName, fieldType, columnLabel,
				columnName, 0, // Figure out precision
				decimals, charSet, length, flags, fieldDefault);
		fields[fieldIndex++] = field;
		return new ResultSetFieldResponse(packetLength, packetNumber, field);
	}

	protected EofResponse decodeEofResponse(IoBuffer buffer, int length, byte packetNumber,
			EofResponse.Type type) {
		// Create EOF response
		byte fieldCount = buffer.get();

		if (fieldCount != RESPONSE_EOF) {
			throw new MysqlException(connection, "Expected an EOF response from the server");
		}

		int warnings = buffer.getUnsignedShort();
		Set<ServerStatus> serverStatus = buffer.getEnumSetShort(ServerStatus.class);

		return new EofResponse(length, packetNumber, warnings, serverStatus, type);
	}

	private long getBinaryLengthEncoding(IoBuffer buffer) {
		// This is documented at
		// http://forge.mysql.com/wiki/MySQL_Internals_ClientServer_Protocol#Elements
		int firstByte = buffer.getUnsigned();
		if (firstByte <= 250) {
			return firstByte;
		}
		if (firstByte == NULL_VALUE) {
			return -1;
		}
		if (firstByte == 252) {
			return buffer.getUnsignedShort();
		}
		if (firstByte == 253) {
			return buffer.getMediumInt();
		}
		if (firstByte == 254) {
			long length = buffer.getLong();
			if (length < 0) {
				throw new DbException(connection, "Received length too large to handle");
			}
			return length;
		}
		throw new DbException(connection, "Recieved a length value we don't know how to handle");
	}

	private String decodeLengthCodedString(IoBuffer buffer)
			throws CharacterCodingException {
		MysqlCharacterSet charSet = connection.getCharacterSet();
		long length = getBinaryLengthEncoding(buffer);
		if (length > Integer.MAX_VALUE) {
			throw new MysqlException(connection, "String too long to decode");
		}
		// TODO Add support to MINA for reading fixed length strings that may contain nulls
		return buffer.getString((int)length, charSet.getCharset().newDecoder());
	}

}
