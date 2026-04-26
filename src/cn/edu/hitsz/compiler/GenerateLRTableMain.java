package cn.edu.hitsz.compiler;

import cn.edu.hitsz.compiler.lexer.TokenKind;
import cn.edu.hitsz.compiler.parser.table.TableGenerator;
import cn.edu.hitsz.compiler.utils.FileUtils;

/**
 * @author Yangxin Wu
 * @date 2026/4/26 21:31
 */
public class GenerateLRTableMain {
    public static void main(String[] args) {
        TokenKind.loadTokenKinds();
        final var generator = new TableGenerator();
        generator.run();

        final var outPath = System.getProperty("hitsz.lrTableOutPath", "data/out/lrTable.csv");
        generator.getTable().dumpTable(outPath);

        // TableLoader 不接受空状态行, 这里统一剔除空行后再落盘
        final var cleanedLines = FileUtils.readLines(outPath).stream()
                .filter(line -> !line.isBlank())
                .toList();
        FileUtils.writeLines(outPath, cleanedLines);
    }
}