package org.example;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Main extends JFrame {

    private JComboBox<Integer> yearCombo;
    private JComboBox<Integer> monthCombo;
    private JRadioButton a4PrintRadio;
    private JRadioButton a3DigitalRadio;
    private JButton generateButton;
    private JProgressBar progressBar;

    private static String holidayText = "";

    public Main() {
        setTitle("Perfect Notebook Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(480, 300);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JPanel settingsPanel = new JPanel(new GridLayout(3, 2, 10, 20));
        settingsPanel.add(new JLabel("対象の年 (Year):"));
        yearCombo = new JComboBox<>();
        for (int y = 2025; y <= 2035; y++) yearCombo.addItem(y);
        yearCombo.setSelectedItem(2026);
        settingsPanel.add(yearCombo);

        settingsPanel.add(new JLabel("対象の月 (Month):"));
        monthCombo = new JComboBox<>();
        for (int m = 1; m <= 12; m++) monthCombo.addItem(m);
        monthCombo.setSelectedItem(5);
        settingsPanel.add(monthCombo);

        settingsPanel.add(new JLabel("出力形式 (Layout):"));
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        a4PrintRadio = new JRadioButton("A4 印刷用", true);
        a3DigitalRadio = new JRadioButton("A3 デジタル", false);
        ButtonGroup group = new ButtonGroup();
        group.add(a4PrintRadio);
        group.add(a3DigitalRadio);
        radioPanel.add(a4PrintRadio);
        radioPanel.add(a3DigitalRadio);
        settingsPanel.add(radioPanel);

        JPanel actionPanel = new JPanel(new BorderLayout(8, 8));
        generateButton = new JButton("PDFリフィルを生成する");
        generateButton.setPreferredSize(new Dimension(0, 45));
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("スタンバイ");
        actionPanel.add(generateButton, BorderLayout.NORTH);
        actionPanel.add(progressBar, BorderLayout.SOUTH);

        mainPanel.add(settingsPanel, BorderLayout.CENTER);
        mainPanel.add(actionPanel, BorderLayout.SOUTH);
        add(mainPanel);

        generateButton.addActionListener(e -> startGeneration());
    }

    private void startGeneration() {
        int year = (int) yearCombo.getSelectedItem();
        int month = (int) monthCombo.getSelectedItem();
        boolean isPrint = a4PrintRadio.isSelected();

        generateButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("祝日データを読込中...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                fetchHolidays();
                return isPrint ? createPrintA4(year, month) : createDigitalA3(year, month);
            }
            @Override
            protected void done() {
                try {
                    String path = get();
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("完了!");
                    JOptionPane.showMessageDialog(Main.this, "保存完了: " + new File(path).getAbsolutePath());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(Main.this, "エラー: " + ex.getMessage());
                } finally {
                    generateButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void fetchHolidays() {
        if (!holidayText.isEmpty()) return;
        try {
            URL url = URI.create("https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv").toURL();
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "Shift_JIS"))) {
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append("\n");
                holidayText = sb.toString();
            }
        } catch (Exception ignored) {}
    }

    private static boolean checkHoliday(Calendar cal) {
        SimpleDateFormat s1 = new SimpleDateFormat("yyyy/M/d,");
        SimpleDateFormat s2 = new SimpleDateFormat("yyyy/MM/dd,");
        String t1 = s1.format(cal.getTime());
        String t2 = s2.format(cal.getTime());
        return holidayText.contains(t1) || holidayText.contains(t2);
    }

    private PDType0Font getFont(PDDocument doc) throws Exception {
        // 【最優先】プロジェクト内のリソースフォルダ(msgothic.ttc)から読み込む（GitHub Actions環境対策）
        try (InputStream fontStream = Main.class.getClassLoader().getResourceAsStream("msgothic.ttc")) {
            if (fontStream != null) {
                // InputStreamを一時ファイルに書き出す、またはTrueTypeCollectionへ直接ストリームとして渡す
                // PDFBox 3.x では、TTCリソースから特定のフォントを抽出するために一時的にファイルへ落とすのが最も安定します
                File tempFontFile = File.createTempFile("msgothic", ".ttc");
                tempFontFile.deleteOnExit();
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(tempFontFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fontStream.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                TrueTypeCollection ttc = new TrueTypeCollection(tempFontFile);
                TrueTypeFont ttf = ttc.getFontByName("MS-Gothic");
                if (ttf == null) ttf = ttc.getFontByName("MS-Gothic-Regular");
                if (ttf != null) {
                    return PDType0Font.load(doc, ttf, true);
                }
            }
        } catch (Exception ignored) {
            // リソースからの読み込みに失敗した場合は、以下のローカルフォント探索にフォールバックします
        }

        // ローカル環境用のフォールバック設定
        File f = new File("C:/Windows/Fonts/msgothic.ttc");
        if (!f.exists()) f = new File("/System/Library/Fonts/Hinted/MS-Gothic.ttf");
        if (!f.exists()) f = new File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");

        if (f.exists() && f.getName().endsWith(".ttc")) {
            TrueTypeCollection ttc = new TrueTypeCollection(f);
            TrueTypeFont ttf = ttc.getFontByName("MS-Gothic");
            if (ttf == null) ttf = ttc.getFontByName("MS-Gothic-Regular");
            return PDType0Font.load(doc, ttf, true);
        }
        return PDType0Font.load(doc, f.exists() ? f : new File("C:/Windows/Fonts/arial.ttf"));
    }

    // --- 共通の横線描画 (30分点線) ---
    private void drawLines(PDPageContentStream cs, PDType0Font font, float ox, float uw, float sy, float rh, float tw, float dw, boolean[] monthArr) throws Exception {
        for (int r = 0; r <= 48; r++) {
            float y = sy - 25 - (r * rh);
            boolean isHr = (r % 2 == 0);
            if (isHr) { cs.setStrokingColor(new Color(180,180,180)); cs.setLineWidth(0.8f); cs.setLineDashPattern(new float[]{}, 0); }
            else { cs.setStrokingColor(new Color(215,215,215)); cs.setLineWidth(0.5f); cs.setLineDashPattern(new float[]{2,2}, 0); }
            if (tw > 0) { cs.moveTo(ox, y); cs.lineTo(ox + tw, y); cs.stroke(); }
            for (int i = 0; i < monthArr.length; i++) {
                if (monthArr[i]) {
                    float x = ox + tw + (i * dw);
                    cs.moveTo(x, y); cs.lineTo(x + dw, y); cs.stroke();
                }
            }
            if (tw > 0 && isHr && r < 48) {
                cs.beginText(); cs.setFont(font, 8); cs.setNonStrokingColor(Color.DARK_GRAY);
                cs.newLineAtOffset(ox + 4, y - 11); cs.showText(String.format("%02d:00", r/2)); cs.endText();
            }
        }
        cs.setLineDashPattern(new float[]{}, 0); cs.setStrokingColor(new Color(200,200,200)); cs.setLineWidth(0.7f);
        float bY = sy - 25 - (48 * rh);
        cs.moveTo(ox, sy); cs.lineTo(ox, bY); cs.stroke();
        if (tw > 0) { cs.moveTo(ox + tw, sy); cs.lineTo(ox + tw, bY); cs.stroke(); }
        for (int i = 0; i < monthArr.length; i++) {
            float x = ox + tw + ((i+1) * dw);
            if (monthArr[i] || (i > 0 && monthArr[i-1])) { cs.moveTo(x, sy); cs.lineTo(x, bY); cs.stroke(); }
        }
    }

    // --- A4印刷用生成 ---
    private String createPrintA4(int year, int month) throws Exception {
        String name = "Planner_A4_Print_" + year + "_" + month + ".pdf";
        try (PDDocument doc = new PDDocument()) {
            PDType0Font font = getFont(doc);
            PDPage p1 = new PDPage(PDRectangle.A4); doc.addPage(p1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) { drawA4Monthly(cs, font, year, month); }

            Calendar cal = Calendar.getInstance(); cal.set(year, month - 1, 1);
            int diff = cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY; if (diff < 0) diff += 7;
            cal.add(Calendar.DAY_OF_MONTH, -diff);
            Calendar end = Calendar.getInstance(); end.set(year, month - 1, 1);
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));

            int pIdx = 2;
            while (cal.before(end) || cal.get(Calendar.MONTH) == (month - 1)) {
                PDPage lp = new PDPage(PDRectangle.A4); doc.addPage(lp);
                try (PDPageContentStream cs = new PDPageContentStream(doc, lp)) { drawA4Weekly(cs, font, (Calendar)cal.clone(), month, pIdx, true); }
                pIdx++;
                PDPage rp = new PDPage(PDRectangle.A4); doc.addPage(rp);
                try (PDPageContentStream cs = new PDPageContentStream(doc, rp)) {
                    Calendar rCal = (Calendar)cal.clone(); rCal.add(Calendar.DAY_OF_MONTH, 4);
                    drawA4Weekly(cs, font, rCal, month, pIdx, false);
                }
                pIdx++;
                cal.add(Calendar.DAY_OF_MONTH, 7);
            }
            doc.save(name);
        }
        return name;
    }

    private void drawA4Monthly(PDPageContentStream cs, PDType0Font font, int year, int month) throws Exception {
        float ml = 65, mt = 100, uw = PDRectangle.A4.getWidth() - ml - 25;
        float cw = uw / 7, ch = 85, sy = PDRectangle.A4.getHeight() - mt;
        cs.beginText(); cs.setFont(font, 18); cs.newLineAtOffset(ml, sy + 40);
        cs.showText(year + "年 " + month + "月 月間カレンダー (A4印刷用)"); cs.endText();

        String[] days = {"月", "火", "水", "木", "金", "土", "日"};
        for (int i = 0; i < 7; i++) {
            cs.beginText(); cs.setFont(font, 10);
            if (i == 5) cs.setNonStrokingColor(new Color(0,0,200)); else if (i == 6) cs.setNonStrokingColor(new Color(200,0,0));
            else cs.setNonStrokingColor(Color.DARK_GRAY);
            cs.newLineAtOffset(ml + (i * cw) + (cw/2) - 6, sy + 15); cs.showText(days[i]); cs.endText();
        }

        Calendar cal = Calendar.getInstance(); cal.set(year, month - 1, 1);
        int max = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int gridStart = cal.get(Calendar.DAY_OF_WEEK) - 1; if (gridStart == 0) gridStart = 7;
        int curGrid = 1, maxRow = 4;
        for (int d = 1; d <= max; d++) {
            while (curGrid < gridStart) curGrid++;
            int col = (curGrid - 1) % 7, row = (curGrid - 1) / 7; if (row > maxRow) maxRow = row;
            float cx = ml + (col * cw), cy = sy - ((row + 1) * ch);
            cal.set(year, month - 1, d); boolean hol = checkHoliday(cal); int wd = cal.get(Calendar.DAY_OF_WEEK);
            if (hol || wd == Calendar.SUNDAY) { cs.setNonStrokingColor(new Color(255,235,235)); cs.addRect(cx, cy, cw, ch); cs.fill(); }
            else if (wd == Calendar.SATURDAY) { cs.setNonStrokingColor(new Color(230,240,255)); cs.addRect(cx, cy, cw, ch); cs.fill(); }
            cs.beginText(); cs.setFont(font, 10);
            if (hol || wd == Calendar.SUNDAY) cs.setNonStrokingColor(new Color(200,0,0)); else if (wd == Calendar.SATURDAY) cs.setNonStrokingColor(new Color(0,0,200));
            else cs.setNonStrokingColor(Color.BLACK);
            cs.newLineAtOffset(cx + 6, cy + ch - 15); cs.showText(String.valueOf(d)); cs.endText();
            curGrid++;
        }
        cs.setStrokingColor(Color.LIGHT_GRAY); cs.setLineWidth(0.7f);
        for (int r = 0; r <= maxRow + 1; r++) { cs.moveTo(ml, sy - (r * ch)); cs.lineTo(ml + (7 * cw), sy - (r * ch)); cs.stroke(); }
        for (int c = 0; c <= 7; c++) { cs.moveTo(ml + (c * cw), sy); cs.lineTo(ml + (c * cw), sy - ((maxRow + 1) * ch)); cs.stroke(); }
    }

    private void drawA4Weekly(PDPageContentStream cs, PDType0Font font, Calendar cal, int targetM, int pNum, boolean isLeft) throws Exception {
        float ml = (pNum % 2 != 0) ? 65 : 25, mr = (pNum % 2 != 0) ? 25 : 65;
        float sy = PDRectangle.A4.getHeight() - 60, uw = PDRectangle.A4.getWidth() - ml - mr;
        float tw = isLeft ? 35 : 0, dw = (uw - tw) / 4, rh = 14;
        String[] names = isLeft ? new String[]{"月","火","水","木"} : new String[]{"金","土","日","メモ"};
        boolean[] currArr = new boolean[4];
        for (int i = 0; i < 4; i++) {
            float x = ml + tw + (i * dw);
            if (!isLeft && i == 3) { currArr[i] = true; cs.setNonStrokingColor(new Color(240,240,240)); cs.addRect(x, sy-25, dw, 25); cs.fill(); cs.beginText(); cs.setFont(font, 9); cs.setNonStrokingColor(Color.BLACK); cs.newLineAtOffset(x+10, sy-16); cs.showText("メモ"); cs.endText(); continue; }
            boolean cM = (cal.get(Calendar.MONTH) == targetM - 1); currArr[i] = cM;
            if (!cM) { cs.setNonStrokingColor(new Color(245,245,245)); cs.addRect(x, sy-25, dw, 25); cs.fill(); cal.add(Calendar.DAY_OF_MONTH, 1); continue; }
            boolean h = checkHoliday(cal); int wd = cal.get(Calendar.DAY_OF_WEEK);
            if (h || wd == Calendar.SUNDAY) cs.setNonStrokingColor(new Color(255,242,242)); else if (wd == Calendar.SATURDAY) cs.setNonStrokingColor(new Color(242,248,255));
            else cs.setNonStrokingColor(new Color(252,252,252));
            cs.addRect(x, sy-25, dw, 25); cs.fill();
            cs.beginText(); cs.setFont(font, 9);
            if (h || wd == Calendar.SUNDAY) cs.setNonStrokingColor(new Color(200,0,0)); else if (wd == Calendar.SATURDAY) cs.setNonStrokingColor(new Color(0,0,200));
            else cs.setNonStrokingColor(Color.BLACK);
            cs.newLineAtOffset(x+6, sy-16); cs.showText((cal.get(Calendar.MONTH)+1)+"/"+cal.get(Calendar.DAY_OF_MONTH)+" ("+names[i]+")"); cs.endText();
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        drawLines(cs, font, ml, uw, sy, rh, tw, dw, currArr);
    }

    // --- A3デジタル用生成 ---
    private String createDigitalA3(int year, int month) throws Exception {
        float w = PDRectangle.A3.getHeight(), h = PDRectangle.A3.getWidth();
        String name = "Planner_A3_Digital_" + year + "_" + month + ".pdf";
        try (PDDocument doc = new PDDocument()) {
            PDType0Font font = getFont(doc);
            PDPage p1 = new PDPage(new PDRectangle(w, h)); doc.addPage(p1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) {
                float margin = 25, sy = h - 120, calW = (w / 2) - margin, cw = calW / 7, ch = 85;
                cs.beginText(); cs.setFont(font, 22); cs.setNonStrokingColor(Color.BLACK); cs.newLineAtOffset(margin, h - 60);
                cs.showText(year + "年 " + month + "月 デジタルプランナー"); cs.endText();

                String[] dHead = {"月","火","水","木","金","土","日"};
                for (int i=0; i<7; i++){
                    cs.beginText(); cs.setFont(font, 10); cs.setNonStrokingColor(Color.DARK_GRAY);
                    cs.newLineAtOffset(margin + (i*cw) + (cw/2) - 6, sy + 15); cs.showText(dHead[i]); cs.endText();
                }

                Calendar cal = Calendar.getInstance(); cal.set(year, month - 1, 1);
                int start = cal.get(Calendar.DAY_OF_WEEK) - 1; if (start == 0) start = 7;
                int cur = 1, maxR = 4;
                for (int d = 1; d <= cal.getActualMaximum(Calendar.DAY_OF_MONTH); d++) {
                    while (cur < start) cur++;
                    int c = (cur-1)%7, r = (cur-1)/7; if (r > maxR) maxR = r;
                    float cx = margin + (c*cw), cy = sy - ((r+1)*ch);
                    cal.set(year, month - 1, d); boolean hol = checkHoliday(cal); int wd = cal.get(Calendar.DAY_OF_WEEK);
                    if (hol || wd == Calendar.SUNDAY) { cs.setNonStrokingColor(new Color(255,242,242)); cs.addRect(cx,cy,cw,ch); cs.fill(); }
                    else if (wd == Calendar.SATURDAY) { cs.setNonStrokingColor(new Color(242,248,255)); cs.addRect(cx,cy,cw,ch); cs.fill(); }
                    cs.beginText(); cs.setFont(font, 11);
                    if (hol || wd == Calendar.SUNDAY) cs.setNonStrokingColor(new Color(220,50,50)); else if (wd == Calendar.SATURDAY) cs.setNonStrokingColor(new Color(50,50,220));
                    else cs.setNonStrokingColor(Color.BLACK);
                    cs.newLineAtOffset(cx+8, cy+ch-18); cs.showText(String.valueOf(d)); cs.endText();
                    cur++;
                }
                cs.setStrokingColor(new Color(180,180,180)); cs.setLineWidth(0.8f);
                for(int r=0; r<=maxR+1; r++){ cs.moveTo(margin, sy-(r*ch)); cs.lineTo(margin+(7*cw), sy-(r*ch)); cs.stroke(); }
                for(int c=0; c<=7; c++){ cs.moveTo(margin+(c*cw), sy); cs.lineTo(margin+(c*cw), sy-((maxR+1)*ch)); cs.stroke(); }
            }

            Calendar cal = Calendar.getInstance(); cal.set(year, month - 1, 1);
            int diff = cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY; if (diff < 0) diff += 7;
            cal.add(Calendar.DAY_OF_MONTH, -diff);
            Calendar end = Calendar.getInstance(); end.set(year, month - 1, 1);
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));

            while (cal.before(end) || cal.get(Calendar.MONTH) == (month - 1)) {
                PDPage wp = new PDPage(new PDRectangle(w, h)); doc.addPage(wp);
                try (PDPageContentStream cs = new PDPageContentStream(doc, wp)) {
                    float m = 25, sy = h - 60, tw = 35, dw = (w - (m*2) - tw) / 8, rh = 14;
                    boolean[] cArr = new boolean[8];
                    for (int i = 0; i < 8; i++) {
                        float x = m + tw + (i * dw);
                        if (i == 7) { cArr[i] = true; cs.setNonStrokingColor(new Color(235,240,245)); cs.addRect(x, sy-25, dw, 25); cs.fill(); cs.beginText(); cs.setFont(font, 10); cs.setNonStrokingColor(new Color(50,70,90)); cs.newLineAtOffset(x+15, sy-17); cs.showText("NOTES"); cs.endText(); continue; }
                        boolean cM = (cal.get(Calendar.MONTH) == month - 1); cArr[i] = cM;
                        if (!cM) { cs.setNonStrokingColor(new Color(245,245,245)); cs.addRect(x, sy-25, dw, 25); cs.fill(); cal.add(Calendar.DAY_OF_MONTH, 1); continue; }
                        boolean hld = checkHoliday(cal); int wd = cal.get(Calendar.DAY_OF_WEEK);
                        if (hld || wd == Calendar.SUNDAY) cs.setNonStrokingColor(new Color(255,242,242)); else if (wd == Calendar.SATURDAY) cs.setNonStrokingColor(new Color(242,248,255));
                        else cs.setNonStrokingColor(new Color(252,252,252));
                        cs.addRect(x, sy-25, dw, 25); cs.fill();
                        cs.beginText(); cs.setFont(font, 11);
                        if (hld || wd == Calendar.SUNDAY) cs.setNonStrokingColor(new Color(220,50,50)); else if (wd == Calendar.SATURDAY) cs.setNonStrokingColor(new Color(50,50,220));
                        else cs.setNonStrokingColor(Color.BLACK);
                        cs.newLineAtOffset(x+8, sy-17); cs.showText((cal.get(Calendar.MONTH)+1)+"/"+cal.get(Calendar.DAY_OF_MONTH)); cs.endText();
                        cal.add(Calendar.DAY_OF_MONTH, 1);
                    }
                    drawLines(cs, font, m, w-(m*2), sy, rh, tw, dw, cArr);
                }
            }
            doc.save(name);
        }
        return name;
    }

    // ★GitHub Actions(画面なし)から直接呼び出して実行する秘密の裏ルート
    private static void runAsCommandLine(int year, int month, String type) {
        System.out.println("GitHub自動実行モードで起動: " + year + "年 " + month + "月 [" + type + "]");
        try {
            Main app = new Main();
            app.fetchHolidays(); // 祝日の取得

            String generatedFile;
            if ("A4_Print".equalsIgnoreCase(type)) {
                generatedFile = app.createPrintA4(year, month);
            } else {
                generatedFile = app.createDigitalA3(year, month);
            }
            System.out.println("PDFの自動生成に成功しました -> " + generatedFile);
        } catch (Exception e) {
            System.err.println("自動生成エラーが発生しました:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        // 💡 この下の1行を、mainメソッドの一番最初に追加します！
        System.setProperty("java.awt.headless", "true");
        // もしGitHub Actionsから「年」「月」「タイプ」が送られてきたら、画面を出さずに直接PDFを作る
        if (args.length >= 3) {
            try {
                int year = Integer.parseInt(args[0]);
                int month = Integer.parseInt(args[1]);
                String type = args[2]; // "A4_Print" もしくは "A3_Digital"
                runAsCommandLine(year, month, type);
                return; // ここで終了
            } catch (NumberFormatException e) {
                System.out.println("引数不正のため、通常起動にフォールバックします。");
            }
        }

        // 引数がない(あなたのPCで普通に動かした)場合は、これまで通りデスクトップ画面を起動
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new Main().setVisible(true);
        });
    }
}