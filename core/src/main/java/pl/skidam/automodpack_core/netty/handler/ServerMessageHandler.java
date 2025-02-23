package pl.skidam.automodpack_core.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import pl.skidam.automodpack_core.GlobalVariables;
import pl.skidam.automodpack_core.auth.Secrets;
import pl.skidam.automodpack_core.modpack.ModpackContent;
import pl.skidam.automodpack_core.netty.message.EchoMessage;
import pl.skidam.automodpack_core.netty.message.FileRequestMessage;
import pl.skidam.automodpack_core.netty.message.ProtocolMessage;
import pl.skidam.automodpack_core.netty.message.RefreshRequestMessage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static pl.skidam.automodpack_core.GlobalVariables.*;
import static pl.skidam.automodpack_core.netty.NetUtils.*;

public class ServerMessageHandler extends SimpleChannelInboundHandler<ProtocolMessage> {

    private static byte clientProtocolVersion = 0;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage msg) throws Exception {
        clientProtocolVersion = msg.getVersion();
        SocketAddress address = ctx.channel().remoteAddress();

        // Validate the secret
        if (!validateSecret(address, msg.getSecret())) {
            sendError(ctx, clientProtocolVersion, "Authentication failed");
            return;
        }

        switch (msg.getType()) {
            case ECHO_TYPE:
                EchoMessage echoMsg = (EchoMessage) msg;
                ByteBuf echoBuf = Unpooled.buffer(1 + 1 + msg.getSecret().length + echoMsg.getData().length);
                echoBuf.writeByte(clientProtocolVersion);
                echoBuf.writeByte(ECHO_TYPE);
                echoBuf.writeBytes(echoMsg.getSecret());
                echoBuf.writeBytes(echoMsg.getData());
                ctx.writeAndFlush(echoBuf);
                ctx.channel().close();
                break;
            case FILE_REQUEST_TYPE:
                FileRequestMessage fileRequest = (FileRequestMessage) msg;
                sendFile(ctx, fileRequest.getFileHash());
                break;
            case REFRESH_REQUEST_TYPE:
                RefreshRequestMessage refreshRequest = (RefreshRequestMessage) msg;
                refreshModpackFiles(ctx, refreshRequest.getFileHashesList());
                break;
            default:
                sendError(ctx, clientProtocolVersion, "Unknown message type");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private void refreshModpackFiles(ChannelHandlerContext context, byte[][] FileHashesList) {
        List<String> hashes = new ArrayList<>();
        for (byte[] hash : FileHashesList) {
            hashes.add(new String(hash));
        }
        LOGGER.info("Received refresh request for files of hashes: {}", hashes);
        List<CompletableFuture<Void>> creationFutures = new ArrayList<>();
        List<ModpackContent> modpacks = new ArrayList<>();
        for (String hash : hashes) {
            final Optional<Path> optionalPath = resolvePath(hash);
            if (optionalPath.isEmpty()) continue;
            Path path = optionalPath.get();
            ModpackContent modpack = null;
            for (var content : GlobalVariables.modpack.modpacks.values()) {
                if (!content.pathsMap.getMap().containsKey(hash)) {
                    continue;
                }

                modpack = content;
                break;
            }

            if (modpack == null) {
                continue;
            }

            modpacks.add(modpack);

            creationFutures.add(modpack.replaceAsync(path));
        }

        creationFutures.forEach(CompletableFuture::join);
        modpacks.forEach(ModpackContent::saveModpackContent);

        LOGGER.info("Sending new modpack-content.json");

        // Sends new json
        sendFile(context, new byte[0]);
    }


    private boolean validateSecret(SocketAddress address, byte[] secret) {
        String decodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return Secrets.isSecretValid(decodedSecret, address);
    }

    private void sendFile(ChannelHandlerContext ctx, byte[] bsha1) {
        final String sha1 = new String(bsha1, CharsetUtil.UTF_8);
        final Optional<Path> optionalPath = resolvePath(sha1);

        if (optionalPath.isEmpty() || !Files.exists(optionalPath.get())) {
            sendError(ctx, (byte) 1, "File not found");
            return;
        }

        final File file = optionalPath.get().toFile();

        // Send file response header: version, FILE_RESPONSE type, then file size (8 bytes)
        ByteBuf responseHeader = Unpooled.buffer(1 + 1 + 8);
        responseHeader.writeByte(clientProtocolVersion);
        responseHeader.writeByte(FILE_RESPONSE_TYPE);
        responseHeader.writeLong(file.length());
        ctx.writeAndFlush(responseHeader);

        // Stream the file using ChunkedFile (chunk size set to 8192 bytes)
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            ChunkedFile chunkedFile = new ChunkedFile(raf, 0, raf.length(), 8192);
            ctx.writeAndFlush(chunkedFile).addListener((ChannelFutureListener) future -> {
                // After the file is sent, send an End-of-Transmission message.
                ByteBuf eot = Unpooled.buffer(2);
                eot.writeByte((byte) 1);
                eot.writeByte(END_OF_TRANSMISSION);
                ctx.writeAndFlush(eot);
            });
        } catch (IOException e) {
            sendError(ctx, (byte) 1, "File transfer error: " + e.getMessage());
        }
    }

    public Optional<Path> resolvePath(final String sha1) {
        if (sha1.isBlank()) {
            return Optional.of(hostModpackContentFile);
        }

        return hostServer.getPath(sha1);
    }

    private void sendError(ChannelHandlerContext ctx, byte version, String errorMessage) {
        byte[] errMsgBytes = errorMessage.getBytes(CharsetUtil.UTF_8);
        ByteBuf errorBuf = Unpooled.buffer(1 + 1 + 4 + errMsgBytes.length);
        errorBuf.writeByte(version);
        errorBuf.writeByte(ERROR);
        errorBuf.writeInt(errMsgBytes.length);
        errorBuf.writeBytes(errMsgBytes);
        ctx.writeAndFlush(errorBuf);
        ctx.channel().close();
    }
}
