package com.fc.keycloak.userstorage.repository;

import com.fc.keycloak.userstorage.model.UserEntity;
import lombok.RequiredArgsConstructor;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

@RequiredArgsConstructor
public class JdbcUserRepository implements UserRepository {

    private final DataSource dataSource;


    @Override
    public Optional<UserEntity> findById(Long id) {
        String sql = """
            select *
            from users
            where id = ?
              and deleted_at is null
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new RuntimeException("findById 실패", e);
        }
    }

    @Override
    public Optional<UserEntity> findByUserId(String userId) {
        String sql = """
            select *
            from users
            where user_id = ?
              and deleted_at is null
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new RuntimeException("findByUserId 실패", e);
        }
    }

    @Override
    public boolean existsByUserId(String userId) {
        String sql = """
            select 1
            from users
            where user_id = ?
              and deleted_at is null
            limit 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            throw new RuntimeException("existsByUserId 실패", e);
        }
    }

    @Override
    public boolean existsByNickname(String nickname) {
        String sql = """
            select 1
            from users
            where nickname = ?
              and deleted_at is null
            limit 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nickname);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            throw new RuntimeException("existsByNickname 실패", e);
        }
    }

    @Override
    public UserEntity save(UserEntity user) {
        String sql = """
            insert into users (
                user_id,
                password,
                nickname,
                role,
                points,
                profile_image,
                last_login,
                updated_at,
                deleted_at,
                created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getUserId());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getNickname());
            ps.setString(4, user.getRole());
            ps.setInt(5, user.getPoints() != null ? user.getPoints() : 0);
            ps.setString(6, user.getProfileImage());

            if (user.getLastLogin() != null) {
                ps.setTimestamp(7, Timestamp.valueOf(user.getLastLogin()));
            } else {
                ps.setNull(7, Types.TIMESTAMP);
            }

            if (user.getUpdatedAt() != null) {
                ps.setTimestamp(8, Timestamp.valueOf(user.getUpdatedAt()));
            } else {
                ps.setNull(8, Types.TIMESTAMP);
            }

            if (user.getDeletedAt() != null) {
                ps.setTimestamp(9, Timestamp.valueOf(user.getDeletedAt()));
            } else {
                ps.setNull(9, Types.TIMESTAMP);
            }

            if (user.getCreatedAt() != null) {
                ps.setTimestamp(10, Timestamp.valueOf(user.getCreatedAt()));
            } else {
                ps.setNull(10, Types.TIMESTAMP);
            }

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    user.setId(rs.getLong(1));
                }
            }

            return user;

        } catch (SQLException e) {
            throw new RuntimeException("save 실패", e);
        }
    }

    @Override
    public void updateNickname(Long id, String nickname) {
        String sql = """
            update users
            set nickname = ?,
                updated_at = ?
            where id = ?
              and deleted_at is null
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nickname);
            ps.setTimestamp(2, Timestamp.valueOf(java.time.LocalDateTime.now()));
            ps.setLong(3, id);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("updateNickname 실패", e);
        }
    }


    @Override
    public void updatePassword(Long id, String encodedPassword) {
        String sql = """
            update users
            set password = ?,
                updated_at = ?
            where id = ?
              and deleted_at is null
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, encodedPassword);
            ps.setTimestamp(2, Timestamp.valueOf(java.time.LocalDateTime.now()));
            ps.setLong(3, id);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("updatePassword 실패", e);
        }
    }



    private UserEntity mapRow(ResultSet rs) throws SQLException {
        UserEntity user = new UserEntity();
        user.setId(rs.getLong("id"));
        user.setUserId(rs.getString("user_id"));
        user.setPassword(rs.getString("password"));
        user.setNickname(rs.getString("nickname"));
        user.setRole(rs.getString("role"));
        user.setPoints(rs.getInt("points"));
        user.setProfileImage(rs.getString("profile_image"));

        Timestamp lastLoginTs = rs.getTimestamp("last_login");
        if (lastLoginTs != null) {
            user.setLastLogin(lastLoginTs.toLocalDateTime());
        }

        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        if (updatedAtTs != null) {
            user.setUpdatedAt(updatedAtTs.toLocalDateTime());
        }

        Timestamp deletedAtTs = rs.getTimestamp("deleted_at");
        if (deletedAtTs != null) {
            user.setDeletedAt(deletedAtTs.toLocalDateTime());
        }

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        if (createdAtTs != null) {
            user.setCreatedAt(createdAtTs.toLocalDateTime());
        }

        return user;
    }

}