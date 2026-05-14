package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// TODO: 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {
    private static final class StackItem {
        private final Token token;
        private final SourceCodeType type;

        private StackItem(Token token, SourceCodeType type) {
            this.token = token;
            this.type = type;
        }
    }

    private SymbolTable symbolTable;
    private final Deque<StackItem> semanticStack = new ArrayDeque<>();

    @Override
    public void whenAccept(Status currentStatus) {
        // TODO: 该过程在遇到 Accept 时要采取的代码动作
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO: 该过程在遇到 reduce production 时要采取的代码动作
        final var items = _popItems(production.body().size());

        if (_matches(production, "D", "int")) {
            semanticStack.push(new StackItem(null, SourceCodeType.Int));
            return;
        }

        if (_matches(production, "S", "D", "id")) {
            final var type = items.get(0).type;
            final var idToken = items.get(1).token;
            if (type == null || idToken == null) {
                throw new RuntimeException("Missing type or id token in production: " + production);
            }
            symbolTable.get(idToken.getText()).setType(type);
            semanticStack.push(new StackItem(null, null));
            return;
        }

        if (_isStructuralProduction(production)) {
            semanticStack.push(new StackItem(null, null));
            return;
        }

        throw new RuntimeException("Unhandled production in semantic analyzer: " + production);
    }

    private List<StackItem> _popItems(int count) {
        if (count == 0) {
            return List.of();
        }
        if (semanticStack.size() < count) {
            throw new RuntimeException("Semantic stack underflow");
        }
        final var items = new ArrayList<StackItem>(count);
        for (int i = 0; i < count; i++) {
            items.add(semanticStack.pop());
        }
        for (int i = 0, j = items.size() - 1; i < j; i++, j--) {
            final var temp = items.get(i);
            items.set(i, items.get(j));
            items.set(j, temp);
        }
        return items;
    }

    private boolean _matches(Production production, String head, String... body) {
        if (!production.head().getTermName().equals(head)) {
            return false;
        }
        if (production.body().size() != body.length) {
            return false;
        }
        for (int i = 0; i < body.length; i++) {
            if (!production.body().get(i).getTermName().equals(body[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean _isStructuralProduction(Production production) {
        return _matches(production, "P", "S_list")
                || _matches(production, "S_list", "S", "Semicolon", "S_list")
                || _matches(production, "S_list", "S", "Semicolon")
                || _matches(production, "S", "id", "=", "E")
                || _matches(production, "S", "return", "E")
                || _matches(production, "E", "E", "+", "A")
                || _matches(production, "E", "E", "-", "A")
                || _matches(production, "E", "A")
                || _matches(production, "A", "A", "*", "B")
                || _matches(production, "A", "B")
                || _matches(production, "B", "(", "E", ")")
                || _matches(production, "B", "id")
                || _matches(production, "B", "IntConst");
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO: 该过程在遇到 shift 时要采取的代码动作
        semanticStack.push(new StackItem(currentToken, null));
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO: 设计你可能需要的符号表存储结构
        // 如果需要使用符号表的话, 可以将它或者它的一部分信息存起来, 比如使用一个成员变量存储
        this.symbolTable = table;
    }
}

