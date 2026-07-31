package fr.florianpal.fauction.configurations;

import fr.florianpal.fauction.enums.SQLType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseConfigTest {

    @Test
    @DisplayName("The database settings are read")
    void readsTheSettings() {

        DatabaseConfig config = new DatabaseConfig();
        config.load(TestConfigs.of("""
                database:
                  type: MySQL
                  url: "jdbc:mysql://localhost:3306/fauction"
                  user: "root"
                  password: "secret"
                  maximumPoolSize: 10
                """));

        assertEquals(SQLType.MySQL, config.getSqlType());
        assertEquals("jdbc:mysql://localhost:3306/fauction", config.getUrl());
        assertEquals("root", config.getUser());
        assertEquals("secret", config.getPassword());
        assertEquals(10, config.getMaximumPoolSize());
    }

    @Test
    @DisplayName("The pool size falls back to its default")
    void poolSizeHasADefault() {

        DatabaseConfig config = new DatabaseConfig();
        config.load(TestConfigs.of("""
                database:
                  type: SQLite
                  url: ""
                  user: ""
                  password: ""
                """));

        assertEquals(SQLType.SQLite, config.getSqlType());
        assertEquals(50, config.getMaximumPoolSize());
    }

    @Test
    @DisplayName("An unknown database type is refused instead of being silently ignored")
    void unknownTypeIsRefused() {

        DatabaseConfig config = new DatabaseConfig();

        assertThrows(IllegalArgumentException.class, () -> config.load(TestConfigs.of("""
                database:
                  type: Oracle
                  url: ""
                  user: ""
                  password: ""
                """)));
    }
}