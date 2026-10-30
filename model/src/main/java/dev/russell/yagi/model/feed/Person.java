package dev.russell.yagi.model.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Someone the Podcasting 2.0 {@code <podcast:person>} tag credits for a podcast or an episode:
 * a host, guest, producer and so on. Role and group are stored lower case, as the namespace
 * defines them case-insensitively; a tag without a role means {@link #DEFAULT_ROLE}.
 */
public class Person implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_ROLE = "host";
    public static final String DEFAULT_GROUP = "cast";

    @NonNull
    private final String name;
    @NonNull
    private final String role;
    @NonNull
    private final String group;
    @Nullable
    private final String imageUrl;
    @Nullable
    private final String href;

    public Person(@NonNull String name, @Nullable String role, @Nullable String group,
                  @Nullable String imageUrl, @Nullable String href) {
        this.name = name.trim();
        this.role = normalize(role, DEFAULT_ROLE);
        this.group = normalize(group, DEFAULT_GROUP);
        this.imageUrl = emptyToNull(imageUrl);
        this.href = emptyToNull(href);
    }

    @NonNull
    public String getName() {
        return name;
    }

    /** Lower case, {@link #DEFAULT_ROLE} when the feed gave none. */
    @NonNull
    public String getRole() {
        return role;
    }

    /** Lower case, {@link #DEFAULT_GROUP} when the feed gave none. */
    @NonNull
    public String getGroup() {
        return group;
    }

    @Nullable
    public String getImageUrl() {
        return imageUrl;
    }

    /** A page about the person, null when the feed gave none. */
    @Nullable
    public String getHref() {
        return href;
    }

    /** Whether the person hosts the show ({@code host} or {@code cohost}/{@code co-host}). */
    public boolean isHost() {
        return DEFAULT_ROLE.equals(role) || "cohost".equals(role) || "co-host".equals(role);
    }

    @NonNull
    private static String normalize(@Nullable String value, @NonNull String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Person)) {
            return false;
        }
        Person other = (Person) o;
        return name.equals(other.name) && role.equals(other.role) && group.equals(other.group)
                && Objects.equals(imageUrl, other.imageUrl) && Objects.equals(href, other.href);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, role, group, imageUrl, href);
    }

    @NonNull
    @Override
    public String toString() {
        return "Person{" + name + ", " + role + "}";
    }
}
