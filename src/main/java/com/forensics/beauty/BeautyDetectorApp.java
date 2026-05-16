package com.forensics.beauty;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import com.forensics.beauty.ui.MainWindow;

public class BeautyDetectorApp extends Application {

    // 静态块：加载OpenCV native库
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        MainWindow mainWindow = new MainWindow(primaryStage);

        Scene scene = new Scene(mainWindow.getRoot(), 1440, 900);
        scene.getStylesheets().add(
            getClass().getResource("/styles/dark-theme.css").toExternalForm()
        );

        primaryStage.setTitle("BeautyLens Detector v1.0  ·  Beauty Filter & AI Image Forensics");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}