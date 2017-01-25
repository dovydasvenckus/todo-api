package com.dovydasvenckus.todo;

import com.beust.jcommander.JCommander;
import com.dovydasvenckus.todo.auth.AuthController;
import com.dovydasvenckus.todo.auth.AuthService;
import com.dovydasvenckus.todo.helper.cmd.options.CommandLineOptions;
import com.dovydasvenckus.todo.helper.db.DatabaseConfig;
import com.dovydasvenckus.todo.helper.db.connector.DatabaseConnector;
import com.dovydasvenckus.todo.helper.db.connector.DatabaseConnectorSelector;
import com.dovydasvenckus.todo.todo.TodoController;
import com.dovydasvenckus.todo.util.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sql2o.Sql2o;

import java.util.ArrayList;
import java.util.List;

import static spark.Spark.*;

public class TodoApplication {
    private final static Logger logger = LoggerFactory.getLogger(TodoApplication.class);

    private static DatabaseConnector databaseConnector;
    private static Sql2o sql2o;
    private static List<Controller> controllers = new ArrayList<>();
    private static DatabaseConfig databaseConfig;
    private static AuthService authService;

    public static void main(String[] args) {
        CommandLineOptions options = new CommandLineOptions();
        new JCommander(options, args);
        port(options.getPort() != null ? new Integer(options.getPort()) : 8080);
        loadDatabaseConfig(options);

        staticFiles.location("/public");
        setupSecurityFilter(options);
        initModules();
        logger.info("Finished initialization");
    }

    private static void loadDatabaseConfig(CommandLineOptions options) {
        databaseConfig = new DatabaseConfig(options.getDbUrl(), options.getDbUser(), options.getDbPass());
    }

    private static void setupControllers(Sql2o dbConnection) {
        controllers.add(new TodoController(dbConnection));
        controllers.add(new AuthController(authService));
        controllers.forEach(Controller::setupRoutes);
    }

    private static void setupSecurityFilter(CommandLineOptions options) {
        if (options.getUser() != null && options.getPassword() != null) {
            authService = new AuthService(options.getUser(), options.getPassword());

            before((request, response) -> {
                if (request.uri().startsWith("/api/") && !authService.isAuthorized(request)) {
                    halt(401, "Not authorized");
                }
            });
        }
    }

    private static void initModules() {
        try {
            databaseConnector = (new DatabaseConnectorSelector()).getConnectorInstance(databaseConfig);
            sql2o = databaseConnector.getInstance(databaseConfig);

            setupControllers(sql2o);
        } catch (ClassNotFoundException ex) {
            logger.error("DatabaseDriverEnum connector driver not found", ex);
        }
    }

}
