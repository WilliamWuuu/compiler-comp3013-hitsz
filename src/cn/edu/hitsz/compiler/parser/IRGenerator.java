package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// TODO: 实验三: 实现 IR 生成

/**
 *
 */
public class IRGenerator implements ActionObserver {
    private static final class StackItem {
        private final Token token;
        private final IRValue value;

        private StackItem(Token token, IRValue value) {
            this.token = token;
            this.value = value;
        }
    }

    private SymbolTable symbolTable;
    private final Deque<StackItem> irStack = new ArrayDeque<>();
    private final List<Instruction> instructions = new ArrayList<>();

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO
        irStack.push(new StackItem(currentToken, null));
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO
        final var items = _popItems(production.body().size());

        if (_matches(production, "B", "IntConst")) {
            final var value = Integer.parseInt(items.get(0).token.getText());
            irStack.push(new StackItem(null, IRImmediate.of(value)));
            return;
        }

        if (_matches(production, "B", "id")) {
            irStack.push(new StackItem(null, IRVariable.named(items.get(0).token.getText())));
            return;
        }

        if (_matches(production, "B", "(", "E", ")")) {
            irStack.push(new StackItem(null, items.get(1).value));
            return;
        }

        if (_matches(production, "A", "B") || _matches(production, "E", "A")) {
            irStack.push(new StackItem(null, items.get(0).value));
            return;
        }

        if (_matches(production, "E", "E", "+", "A")) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createAdd(temp, items.get(0).value, items.get(2).value));
            irStack.push(new StackItem(null, temp));
            return;
        }

        if (_matches(production, "E", "E", "-", "A")) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createSub(temp, items.get(0).value, items.get(2).value));
            irStack.push(new StackItem(null, temp));
            return;
        }

        if (_matches(production, "A", "A", "*", "B")) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createMul(temp, items.get(0).value, items.get(2).value));
            irStack.push(new StackItem(null, temp));
            return;
        }

        if (_matches(production, "S", "id", "=", "E")) {
            final var target = IRVariable.named(items.get(0).token.getText());
            instructions.add(Instruction.createMov(target, items.get(2).value));
            irStack.push(new StackItem(null, null));
            return;
        }

        if (_matches(production, "S", "return", "E")) {
            instructions.add(Instruction.createRet(items.get(1).value));
            irStack.push(new StackItem(null, null));
            return;
        }

        if (_matches(production, "S", "D", "id")
                || _matches(production, "D", "int")
                || _matches(production, "P", "S_list")
                || _matches(production, "S_list", "S", "Semicolon", "S_list")
                || _matches(production, "S_list", "S", "Semicolon")) {
            irStack.push(new StackItem(null, null));
            return;
        }

        throw new RuntimeException("Unhandled production in IR generator: " + production);
    }

    private List<StackItem> _popItems(int count) {
        if (count == 0) {
            return List.of();
        }
        if (irStack.size() < count) {
            throw new RuntimeException("IR stack underflow");
        }
        final var items = new ArrayList<StackItem>(count);
        for (int i = 0; i < count; i++) {
            items.add(irStack.pop());
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

    @Override
    public void whenAccept(Status currentStatus) {
        // TODO
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO
        this.symbolTable = table;
    }

    public List<Instruction> getIR() {
        // TODO
        return instructions;
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }
}

