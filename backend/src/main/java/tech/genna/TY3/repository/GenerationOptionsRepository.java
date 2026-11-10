package tech.genna.TY3.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class GenerationOptionsRepository {
    private final JdbcTemplate jdbc;

    public record Option(String category, String value, boolean available, boolean defaultValue) {}

    public List<Option> findAll() {
        // One statement supplies a consistent view of all options and defaults.
        return jdbc.query("""
                SELECT category, option_value, available, default_slot
                FROM generation_options ORDER BY category, sort_order, option_value
                """, (rs, row) -> new Option(rs.getString("category"), rs.getString("option_value"),
                rs.getBoolean("available"), rs.getObject("default_slot") != null));
    }
}
