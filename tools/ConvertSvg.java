import com.android.ide.common.vectordrawable.Svg2Vector;
import com.android.ide.common.vectordrawable.VdPreview;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Uses the Android Gradle plugin's converter and preview renderer; no runtime app dependency. */
class ConvertSvg {
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]), previews = Path.of(args[2]);
        Files.createDirectories(output); Files.createDirectories(previews);
        try (var files = Files.list(input)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".svg")).sorted().toList()) {
                Path target = output.resolve(file.getFileName().toString().replace(".svg", ".xml"));
                String warning;
                try (var stream = Files.newOutputStream(target)) { warning = Svg2Vector.parseSvgToXml(file, stream); }
                System.out.println(file.getFileName() + " conversion: " + warning);
                var errors = new StringBuilder();
                var image = VdPreview.getPreviewFromVectorXml(VdPreview.TargetSize.createFromMaxDimension(1024), Files.readString(target), errors);
                if (image != null) ImageIO.write(image, "png", previews.resolve(file.getFileName().toString().replace(".svg", ".png")).toFile());
                System.out.println("preview: " + errors);
            }
        }
    }
}
