package fr.florianpal.fauction.queries;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs the batch of a statement prepared with {@code RETURN_GENERATED_KEYS} and collects the ids the
 * database assigned.
 */
final class BatchInserts {

    private BatchInserts() {
    }

    /**
     * @return one id per row, in the order of the batch ; every id is {@code null} when the driver
     * does not hand back one key per row (the rows are inserted all the same).
     * @throws SQLException if one row of the batch is refused. Nothing is committed here : the caller
     *                      owns the transaction and rolls it back.
     */
    static List<Integer> execute(PreparedStatement statement, int rows) throws SQLException {
        if (rows == 0) {
            return List.of();
        }

        int[] counts = statement.executeBatch();
        for (int count : counts) {
            if (count == 0) {
                throw new SQLException("A row of the batch was not inserted");
            }
        }

        List<Integer> ids = new ArrayList<>(rows);
        try (ResultSet keys = statement.getGeneratedKeys()) {
            while (keys.next()) {
                ids.add(keys.getInt(1));
            }
        } catch (SQLException e) {
            ids.clear();
        }

        if (ids.size() != rows) {
            return new ArrayList<>(Collections.nCopies(rows, null));
        }
        return ids;
    }
}
