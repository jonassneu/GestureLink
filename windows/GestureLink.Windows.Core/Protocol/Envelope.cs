using System.Text.Json;
using System.Text.Json.Serialization;

namespace GestureLink.Windows.Core.Protocol;

public sealed record Envelope(
    [property: JsonPropertyName("version")] int Version,
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("sessionId")] string SessionId,
    [property: JsonPropertyName("sequence")] long Sequence,
    [property: JsonPropertyName("payload")] JsonElement Payload)
{
    public T PayloadAs<T>() => Payload.Deserialize<T>(ProtocolJson.Options)
        ?? throw new ProtocolException("Payload is missing or invalid.");
}

public static class ProtocolJson
{
    public static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = false,
        NumberHandling = JsonNumberHandling.Strict,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    public static Envelope Create<T>(string type, string sessionId, long sequence, T payload)
    {
        var element = JsonSerializer.SerializeToElement(payload, Options);
        return new Envelope(ProductInfo.ProtocolVersion, type, sessionId, sequence, element);
    }
}

public sealed class ProtocolException(string message) : Exception(message);

public sealed record ClientHelloPayload(string DeviceName, int[] SupportedVersions);
public sealed record PairRequestPayload(string Token, string ClientId, string DeviceName);
public sealed record AuthenticatePayload(string ClientId, string Credential);
public sealed record PointerDeltaPayload(double Dx, double Dy, double Confidence, long SourceFrame);
public sealed record MouseClickPayload(string Button);
public sealed record MouseButtonPayload(string Button, bool Down);
public sealed record ScrollPayload(double Vertical, double Horizontal);
public sealed record SemanticActionPayload(string Action);

