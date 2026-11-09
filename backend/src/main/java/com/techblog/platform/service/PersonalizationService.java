package com.techblog.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techblog.platform.exception.*;
import jakarta.validation.constraints.Size;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class PersonalizationService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PersonalizationService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Navigation(
        @Size(max = 100) List<Long> categoryIds,
        @Size(max = 100) List<String> tagNames
    ) {}

    public record Avatar(byte[] data, String contentType, String revision) {}

    public Navigation navigation(Long userId) {
        var rows = jdbc.query(
            "SELECT category_ids,tag_names FROM user_navigation WHERE user_id=?",
            (r, n) ->
                new Navigation(
                    read(r.getString(1), new TypeReference<List<Long>>() {}),
                    read(r.getString(2), new TypeReference<List<String>>() {})
                ),
            userId
        );
        return rows.isEmpty() ? new Navigation(null, null) : rows.get(0);
    }

    private <T> T read(String value, TypeReference<T> type) {
        if (value == null) return null;
        try {
            return json.readValue(value, type);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid stored navigation", e);
        }
    }

    private String encode(Object value) {
        if (value == null) return null;
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public Navigation saveNavigation(Long userId, Navigation request) {
        if (request.categoryIds() != null) {
            var ids = new HashSet<>(
                jdbc.queryForList("SELECT id FROM categories", Long.class)
            );
            if (
                request.categoryIds().contains(null) ||
                !ids.containsAll(request.categoryIds()) ||
                new HashSet<>(request.categoryIds()).size() !=
                    request.categoryIds().size()
            ) throw new BadRequestException("分类不存在或重复，请刷新后重试");
        }
        if (request.tagNames() != null) {
            var tags = new HashSet<>(
                jdbc.queryForList("SELECT name FROM forum_tags", String.class)
            );
            if (
                request.tagNames().contains(null) ||
                !tags.containsAll(request.tagNames()) ||
                new HashSet<>(request.tagNames()).size() !=
                    request.tagNames().size()
            ) throw new BadRequestException("标签不存在或重复，请刷新后重试");
        }
        jdbc.update(
            "INSERT INTO user_navigation(user_id,category_ids,tag_names) VALUES(?,?,?) ON DUPLICATE KEY UPDATE category_ids=VALUES(category_ids),tag_names=VALUES(tag_names)",
            userId,
            encode(request.categoryIds()),
            encode(request.tagNames())
        );
        return navigation(userId);
    }

    public Avatar avatar(Long userId) {
        var rows = jdbc.query(
            "SELECT image,revision FROM user_avatars WHERE user_id=?",
            (r, n) -> new Avatar(r.getBytes(1), "image/png", r.getString(2)),
            userId
        );
        if (!rows.isEmpty()) return rows.get(0);
        var names = jdbc.queryForList(
            "SELECT username FROM users WHERE id=?",
            String.class,
            userId
        );
        if (names.isEmpty()) throw new NotFoundException("用户不存在");
        String initial = names
            .get(0)
            .substring(0, names.get(0).offsetByCodePoints(0, 1))
            .toUpperCase(Locale.ROOT)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
        String svg =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 128 128\"><rect width=\"128\" height=\"128\" fill=\"#7b9590\"/><text x=\"64\" y=\"88\" text-anchor=\"middle\" font-family=\"sans-serif\" font-size=\"76\" fill=\"white\">" +
            initial +
            "</text></svg>";
        return new Avatar(
            svg.getBytes(StandardCharsets.UTF_8),
            "image/svg+xml",
            "default-" + userId
        );
    }

    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (
            file.isEmpty() || file.getSize() > 2 * 1024 * 1024
        ) throw new BadRequestException(
            "请选择不超过 2 MB 的 PNG 或 JPEG 图片"
        );
        try (
            var input = ImageIO.createImageInputStream(file.getInputStream())
        ) {
            if (input == null) throw new BadRequestException("无法读取图片");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestException(
                "仅支持 PNG 或 JPEG 图片"
            );
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (
                    !format.equalsIgnoreCase("png") &&
                    !format.equalsIgnoreCase("jpeg")
                ) throw new BadRequestException("仅支持 PNG 或 JPEG 图片");
                reader.setInput(input, true, true);
                int w = reader.getWidth(0),
                    h = reader.getHeight(0);
                if (
                    w < 1 || h < 1 || w > 4096 || h > 4096
                ) throw new BadRequestException("图片尺寸不能超过 4096 × 4096");
                var source = reader.read(0);
                var normalized = new BufferedImage(
                    256,
                    256,
                    BufferedImage.TYPE_INT_ARGB
                );
                var graphics = normalized.createGraphics();
                try {
                    graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC
                    );
                    int side = Math.min(w, h),
                        x = (w - side) / 2,
                        y = (h - side) / 2;
                    graphics.drawImage(
                        source,
                        0,
                        0,
                        256,
                        256,
                        x,
                        y,
                        x + side,
                        y + side,
                        null
                    );
                } finally {
                    graphics.dispose();
                }
                var out = new ByteArrayOutputStream();
                ImageIO.write(normalized, "png", out);
                String revision = UUID.randomUUID().toString();
                jdbc.update(
                    "INSERT INTO user_avatars(user_id,image,revision) VALUES(?,?,?) ON DUPLICATE KEY UPDATE image=VALUES(image),revision=VALUES(revision)",
                    userId,
                    out.toByteArray(),
                    revision
                );
                return revision;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("图片损坏或格式不受支持");
        }
    }

    @Transactional
    public void removeAvatar(Long userId) {
        jdbc.update("DELETE FROM user_avatars WHERE user_id=?", userId);
    }
}
