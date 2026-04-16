package fr.florianpal.fauction.configurations;


import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.enums.SQLType;
import lombok.Getter;

@Getter
public class DatabaseConfig {

    private SQLType sqlType;

    private String url;

    private String user;

    private String password;

    private int maximumPoolSize;

    public void load(YamlDocument config) {
        sqlType = SQLType.valueOf(config.getString("database.type"));
        url = config.getString("database.url");
        user = config.getString("database.user");
        password = config.getString("database.password");
        maximumPoolSize = config.getInt("database.maximumPoolSize", 50);
    }

}
