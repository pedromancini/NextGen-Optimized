import com.nextgen.optimizer.App;
import javafx.application.Application;
import javafx.animation.PauseTransition;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public class UiSmoke {
    public static void main(String[] args) { Application.launch(Preview.class, args); }
    public static class Preview extends Application {
        App app;
        Stage stage;
        public void start(Stage window) {
            try {
                stage = window;
                app = new App();
                app.start(window);
                app.getNavigationManager().navigateTo("privacy");
                later(15, () -> {
                    capture("privacy-wide");
                    stage.setWidth(1000); stage.setHeight(650);
                    later(1, () -> {
                        capture("privacy-compact");
                        app.getNavigationManager().navigateTo("dashboard");
                        later(1, () -> {
                            capture("dashboard-compact");
                            app.getNavigationManager().navigateTo("settings");
                            later(2, () -> { capture("settings-compact"); app.exitApplication(); });
                        });
                    });
                });
            } catch (Throwable e) { e.printStackTrace(); System.exit(1); }
        }
        void later(int seconds, Runnable runnable) {
            PauseTransition wait = new PauseTransition(Duration.seconds(seconds));
            wait.setOnFinished(e -> runnable.run()); wait.play();
        }
        void capture(String name) {
            try {
                var image = stage.getScene().snapshot(null);
                BufferedImage bitmap = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < bitmap.getHeight(); y++)
                    for (int x = 0; x < bitmap.getWidth(); x++) bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                ImageIO.write(bitmap, "png", new File("target/" + name + ".png"));
                System.out.println("CAPTURE " + name);
            } catch (Exception e) { e.printStackTrace(); System.exit(1); }
        }
    }
}
