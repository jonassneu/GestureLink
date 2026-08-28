using System.IO;
using System.Text.Json;
using GestureLink.Windows.Core.Protocol;

namespace GestureLink.Windows.Tests;

public sealed class MessageFramingTests
{
    [Fact]
    public async Task RoundTripHandlesPartialReads()
    {
        var envelope = ProtocolJson.Create("pointerDelta", "session", 7, new { dx = 0.1, dy = -0.2, confidence = 0.9, sourceFrame = 12 });
        await using var output = new MemoryStream();
        await MessageFraming.WriteAsync(output, envelope, default);
        await using var partial = new PartialReadStream(output.ToArray(), 1);
        var parsed = await MessageFraming.ReadAsync(partial, default);
        Assert.NotNull(parsed);
        Assert.Equal("pointerDelta", parsed.Type);
        Assert.Equal(7, parsed.Sequence);
    }

    [Fact]
    public async Task RejectsOversizedMessageBeforeAllocatingPayload()
    {
        var prefix = new byte[] { 0, 1, 0, 1 };
        await using var stream = new MemoryStream(prefix);
        await Assert.ThrowsAsync<ProtocolException>(async () => await MessageFraming.ReadAsync(stream, default));
    }

    [Fact]
    public async Task ReadsSharedContractFixture()
    {
        var path = FindRepositoryRoot();
        var json = await File.ReadAllTextAsync(Path.Combine(path, "protocol", "fixtures", "valid-pointer-delta.json"));
        var parsed = JsonSerializer.Deserialize<Envelope>(json, ProtocolJson.Options);
        Assert.NotNull(parsed);
        MessageFraming.Validate(parsed);
        Assert.Equal(1, parsed.Version);
    }

    [Fact]
    public void RejectsWrongVersionFixture()
    {
        var path = FindRepositoryRoot();
        var json = File.ReadAllText(Path.Combine(path, "protocol", "fixtures", "invalid-version.json"));
        var parsed = JsonSerializer.Deserialize<Envelope>(json, ProtocolJson.Options)!;
        Assert.Throws<ProtocolException>(() => MessageFraming.Validate(parsed));
    }

    private static string FindRepositoryRoot()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null && !Directory.Exists(Path.Combine(directory.FullName, "protocol"))) directory = directory.Parent;
        return directory?.FullName ?? throw new DirectoryNotFoundException("Repository root not found.");
    }

    private sealed class PartialReadStream(byte[] bytes, int chunkSize) : Stream
    {
        private readonly MemoryStream _inner = new(bytes);
        public override int Read(byte[] buffer, int offset, int count) => _inner.Read(buffer, offset, Math.Min(count, chunkSize));
        public override int Read(Span<byte> buffer) => _inner.Read(buffer[..Math.Min(buffer.Length, chunkSize)]);
        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken token = default) => _inner.ReadAsync(buffer[..Math.Min(buffer.Length, chunkSize)], token);
        public override bool CanRead => true; public override bool CanSeek => false; public override bool CanWrite => false;
        public override long Length => _inner.Length; public override long Position { get => _inner.Position; set => throw new NotSupportedException(); }
        public override void Flush() { } public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException(); public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }
}
