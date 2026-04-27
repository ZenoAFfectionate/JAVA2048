import java.io.File;
import java.io.IOException;
import javax.sound.sampled.*;

/**
 * 音效播放线程 - 异步加载并播放 .wav 文件
 */
public class PlaySound extends Thread {

    private static final String SOUND_DIR = "res/";
    private final String filePath;

    public PlaySound(String wavFile) {
        this.filePath = SOUND_DIR + wavFile;
    }

    @Override
    public void run() {
        File soundFile = new File(filePath);
        if (!soundFile.exists()) return;

        // try-with-resources 自动关闭流 (Java 7+)
        try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile)) {

            AudioFormat format = audioIn.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(format);
                line.start();

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = audioIn.read(buffer, 0, buffer.length)) != -1) {
                    line.write(buffer, 0, bytesRead);
                }
                line.drain();
            }
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            // 音效加载失败不应中断游戏
            System.err.println("Sound play error: " + e.getMessage());
        }
    }
}
