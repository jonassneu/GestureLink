using System.Buffers.Binary;
using System.IO;
using System.Text.Json;

namespace GestureLink.Windows.Core.Protocol;

public static class MessageFraming
{
    public static async ValueTask WriteAsync(Stream stream, Envelope message, CancellationToken cancellationToken)
    {
        var json = JsonSerializer.SerializeToUtf8Bytes(message, ProtocolJson.Options);
        if (json.Length is <= 0 or > ProductInfo.MaxMessageBytes)
            throw new ProtocolException($"Message length {json.Length} is outside the allowed range.");
        var prefix = new byte[4];
        BinaryPrimitives.WriteUInt32BigEndian(prefix, checked((uint)json.Length));
        await stream.WriteAsync(prefix, cancellationToken).ConfigureAwait(false);
        await stream.WriteAsync(json, cancellationToken).ConfigureAwait(false);
        await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
    }

    public static async ValueTask<Envelope?> ReadAsync(Stream stream, CancellationToken cancellationToken)
    {
        var prefix = new byte[4];
        if (!await ReadExactlyOrEofAsync(stream, prefix, cancellationToken).ConfigureAwait(false)) return null;
        var length = BinaryPrimitives.ReadUInt32BigEndian(prefix);
        if (length is 0 or > ProductInfo.MaxMessageBytes)
            throw new ProtocolException($"Rejected message length {length}.");
        var json = new byte[length];
        await stream.ReadExactlyAsync(json, cancellationToken).ConfigureAwait(false);
        try
        {
            var envelope = JsonSerializer.Deserialize<Envelope>(json, ProtocolJson.Options)
                ?? throw new ProtocolException("JSON envelope is null.");
            Validate(envelope);
            return envelope;
        }
        catch (JsonException error)
        {
            throw new ProtocolException($"Invalid JSON: {error.Message}");
        }
    }

    public static void Validate(Envelope value)
    {
        if (value.Version != ProductInfo.ProtocolVersion) throw new ProtocolException("Unsupported protocol version.");
        if (string.IsNullOrWhiteSpace(value.Type) || value.Type.Length > 40) throw new ProtocolException("Invalid message type.");
        if (string.IsNullOrWhiteSpace(value.SessionId) || value.SessionId.Length > 80) throw new ProtocolException("Invalid session id.");
        if (value.Sequence < 0) throw new ProtocolException("Negative sequence.");
        if (value.Payload.ValueKind != JsonValueKind.Object) throw new ProtocolException("Payload must be an object.");
    }

    private static async ValueTask<bool> ReadExactlyOrEofAsync(Stream stream, Memory<byte> buffer, CancellationToken token)
    {
        var read = 0;
        while (read < buffer.Length)
        {
            var count = await stream.ReadAsync(buffer[read..], token).ConfigureAwait(false);
            if (count == 0)
            {
                if (read == 0) return false;
                throw new EndOfStreamException("Truncated length prefix.");
            }
            read += count;
        }
        return true;
    }
}
