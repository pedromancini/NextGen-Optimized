module com.nextgen.optimizer {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    requires com.github.oshi;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires com.google.gson;

    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    requires org.kordamp.ikonli.core;

    requires java.desktop;
    requires java.management;
    requires jdk.unsupported;

    opens com.nextgen.optimizer to javafx.fxml, javafx.graphics;
    opens com.nextgen.optimizer.ui.pages to javafx.fxml;
    opens com.nextgen.optimizer.ui.components to javafx.fxml;
    opens com.nextgen.optimizer.model to com.google.gson;
    opens com.nextgen.optimizer.tweaks to com.google.gson;

    exports com.nextgen.optimizer;
    exports com.nextgen.optimizer.core;
    exports com.nextgen.optimizer.services;
    exports com.nextgen.optimizer.ui.components;
    exports com.nextgen.optimizer.ui.pages;
    exports com.nextgen.optimizer.model;
    exports com.nextgen.optimizer.overlay;
    exports com.nextgen.optimizer.tweaks;
    exports com.nextgen.optimizer.nativeapi;
}
