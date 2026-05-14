package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * TODO: 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {
    private static final List<String> REGISTERS = List.of("t0", "t1", "t2", "t3", "t4", "t5", "t6");

    private List<Instruction> instructions;
    private List<String> textLines;
    private List<String> dataLines;
    private Map<IRVariable, Integer> lastUse;
    private Map<IRVariable, String> varToReg;
    private Map<String, IRVariable> regToVar;
    private Map<IRVariable, String> spillLabels;
    private Set<String> freeRegs;

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        // TODO: 读入前端提供的中间代码并生成所需要的信息
        this.instructions = _preprocessIR(originInstructions);
        this.lastUse = _computeLastUse(this.instructions);
    }

    private List<Instruction> _preprocessIR(List<Instruction> originInstructions) {
        final var processed = new ArrayList<Instruction>();
        for (final var instruction : originInstructions) {
            switch (instruction.getKind()) {
                case MOV, RET -> processed.add(instruction);
                case ADD -> processed.addAll(_preprocessAdd(instruction));
                case SUB -> processed.addAll(_preprocessSub(instruction));
                case MUL -> processed.addAll(_preprocessMul(instruction));
                default -> throw new RuntimeException("Unknown instruction kind: " + instruction.getKind());
            }
        }
        return processed;
    }

    private List<Instruction> _preprocessAdd(Instruction instruction) {
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();
        if (lhs instanceof IRImmediate lhsImm && rhs instanceof IRImmediate rhsImm) {
            return List.of(Instruction.createMov(instruction.getResult(), IRImmediate.of(lhsImm.getValue() + rhsImm.getValue())));
        }
        if (lhs instanceof IRImmediate && rhs instanceof IRVariable rhsVar) {
            return List.of(Instruction.createAdd(instruction.getResult(), rhsVar, lhs));
        }
        return List.of(instruction);
    }

    private List<Instruction> _preprocessSub(Instruction instruction) {
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();
        if (lhs instanceof IRImmediate lhsImm && rhs instanceof IRImmediate rhsImm) {
            return List.of(Instruction.createMov(instruction.getResult(), IRImmediate.of(lhsImm.getValue() - rhsImm.getValue())));
        }
        if (lhs instanceof IRImmediate lhsImm) {
            final var temp = IRVariable.temp();
            return List.of(
                    Instruction.createMov(temp, IRImmediate.of(lhsImm.getValue())),
                    Instruction.createSub(instruction.getResult(), temp, rhs)
            );
        }
        return List.of(instruction);
    }

    private List<Instruction> _preprocessMul(Instruction instruction) {
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();
        if (lhs instanceof IRImmediate lhsImm && rhs instanceof IRImmediate rhsImm) {
            return List.of(Instruction.createMov(instruction.getResult(), IRImmediate.of(lhsImm.getValue() * rhsImm.getValue())));
        }
        if (lhs instanceof IRImmediate lhsImm) {
            final var temp = IRVariable.temp();
            return List.of(
                    Instruction.createMov(temp, IRImmediate.of(lhsImm.getValue())),
                    Instruction.createMul(instruction.getResult(), temp, rhs)
            );
        }
        if (rhs instanceof IRImmediate rhsImm) {
            final var temp = IRVariable.temp();
            return List.of(
                    Instruction.createMov(temp, IRImmediate.of(rhsImm.getValue())),
                    Instruction.createMul(instruction.getResult(), lhs, temp)
            );
        }
        return List.of(instruction);
    }

    private Map<IRVariable, Integer> _computeLastUse(List<Instruction> instructions) {
        final var lastUse = new HashMap<IRVariable, Integer>();
        for (int i = 0; i < instructions.size(); i++) {
            final var instruction = instructions.get(i);
            switch (instruction.getKind()) {
                case MOV -> _updateLastUse(lastUse, instruction.getFrom(), i);
                case RET -> _updateLastUse(lastUse, instruction.getReturnValue(), i);
                case ADD, SUB, MUL -> {
                    _updateLastUse(lastUse, instruction.getLHS(), i);
                    _updateLastUse(lastUse, instruction.getRHS(), i);
                }
                default -> {
                }
            }
        }
        return lastUse;
    }

    private void _updateLastUse(Map<IRVariable, Integer> lastUse, IRValue value, int index) {
        if (value instanceof IRVariable var) {
            lastUse.put(var, index);
        }
    }


    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        // TODO: 执行寄存器分配与代码生成
        this.textLines = new ArrayList<>();
        this.dataLines = new ArrayList<>();
        this.varToReg = new HashMap<>();
        this.regToVar = new HashMap<>();
        this.spillLabels = new HashMap<>();
        this.freeRegs = new LinkedHashSet<>(REGISTERS);

        for (int index = 0; index < instructions.size(); index++) {
            final var instruction = instructions.get(index);
            switch (instruction.getKind()) {
                case MOV -> _emitMov(instruction, index);
                case ADD -> _emitAdd(instruction, index);
                case SUB -> _emitSub(instruction, index);
                case MUL -> _emitMul(instruction, index);
                case RET -> _emitRet(instruction, index);
                default -> throw new RuntimeException("Unknown instruction kind: " + instruction.getKind());
            }
        }
    }

    private void _emit(String line) {
        textLines.add(line);
    }

    private void _emitMov(Instruction instruction, int index) {
        final var result = instruction.getResult();
        final var from = instruction.getFrom();
        if (from instanceof IRVariable fromVar && fromVar.equals(result)) {
            _ensureInRegister(fromVar, index, Set.of());
            _releaseDeadValues(index, result);
            return;
        }
        if (from instanceof IRImmediate immediate) {
            final var destReg = _allocateForResult(result, index, List.of(), Set.of());
            _emit("    li " + destReg + ", " + immediate.getValue());
        } else if (from instanceof IRVariable fromVar) {
            final var srcReg = _ensureInRegister(fromVar, index, Set.of());
            final var destReg = _allocateForResult(result, index, List.of(fromVar), Set.of(srcReg));
            _emit("    mv " + destReg + ", " + srcReg);
        } else {
            throw new RuntimeException("Unknown MOV operand: " + from);
        }
        _releaseDeadValues(index, result);
    }

    private void _emitAdd(Instruction instruction, int index) {
        final var result = instruction.getResult();
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();
        if (rhs instanceof IRImmediate immediate && lhs instanceof IRVariable lhsVar) {
            final var lhsReg = _ensureInRegister(lhsVar, index, Set.of());
            final var destReg = _allocateForResult(result, index, List.of(lhsVar), Set.of(lhsReg));
            _emit("    addi " + destReg + ", " + lhsReg + ", " + immediate.getValue());
        } else {
            final var lhsReg = _ensureValueInRegister(lhs, index, Set.of());
            final var rhsReg = _ensureValueInRegister(rhs, index, Set.of(lhsReg));
            final var destReg = _allocateForResult(result, index, _collectVars(lhs, rhs), Set.of(lhsReg, rhsReg));
            _emit("    add " + destReg + ", " + lhsReg + ", " + rhsReg);
        }
        _releaseDeadValues(index, result);
    }

    private void _emitSub(Instruction instruction, int index) {
        final var result = instruction.getResult();
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();
        if (rhs instanceof IRImmediate immediate && lhs instanceof IRVariable lhsVar) {
            final var lhsReg = _ensureInRegister(lhsVar, index, Set.of());
            final var destReg = _allocateForResult(result, index, List.of(lhsVar), Set.of(lhsReg));
            _emit("    addi " + destReg + ", " + lhsReg + ", " + (-immediate.getValue()));
        } else {
            final var lhsReg = _ensureValueInRegister(lhs, index, Set.of());
            final var rhsReg = _ensureValueInRegister(rhs, index, Set.of(lhsReg));
            final var destReg = _allocateForResult(result, index, _collectVars(lhs, rhs), Set.of(lhsReg, rhsReg));
            _emit("    sub " + destReg + ", " + lhsReg + ", " + rhsReg);
        }
        _releaseDeadValues(index, result);
    }

    private void _emitMul(Instruction instruction, int index) {
        final var result = instruction.getResult();
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();
        final var lhsReg = _ensureValueInRegister(lhs, index, Set.of());
        final var rhsReg = _ensureValueInRegister(rhs, index, Set.of(lhsReg));
        final var destReg = _allocateForResult(result, index, _collectVars(lhs, rhs), Set.of(lhsReg, rhsReg));
        _emit("    mul " + destReg + ", " + lhsReg + ", " + rhsReg);
        _releaseDeadValues(index, result);
    }

    private void _emitRet(Instruction instruction, int index) {
        final var value = instruction.getReturnValue();
        if (value instanceof IRImmediate immediate) {
            _emit("    li a0, " + immediate.getValue());
        } else if (value instanceof IRVariable var) {
            final var reg = _ensureInRegister(var, index, Set.of());
            _emit("    mv a0, " + reg);
        } else {
            throw new RuntimeException("Unknown RET operand: " + value);
        }
        _releaseDeadValues(index, null);
    }

    private String _ensureInRegister(IRVariable var, int index, Set<String> protectedRegs) {
        final var existing = varToReg.get(var);
        if (existing != null) {
            return existing;
        }
        final var reg = _allocateRegister(var, index, protectedRegs);
        if (spillLabels.containsKey(var)) {
            _emit("    lw " + reg + ", " + spillLabels.get(var));
        }
        return reg;
    }

    private String _allocateRegister(IRVariable var, int index, Set<String> protectedRegs) {
        final var freeReg = _findFreeRegister();
        if (freeReg != null) {
            _bindRegister(var, freeReg);
            return freeReg;
        }
        final var candidate = _selectSpillRegister(index, protectedRegs);
        if (candidate == null) {
            throw new RuntimeException("No available register for " + var);
        }
        final var spillVar = regToVar.get(candidate);
        if (spillVar != null && lastUse.getOrDefault(spillVar, -1) > index) {
            _spillRegister(candidate, spillVar);
        }
        _unbindRegister(candidate);
        _bindRegister(var, candidate);
        return candidate;
    }

    private void _releaseDeadValues(int index, IRVariable resultVar) {
        final var toRelease = new ArrayList<String>();
        for (final var entry : varToReg.entrySet()) {
            final var var = entry.getKey();
            if (var.equals(resultVar)) {
                continue;
            }
            if (lastUse.getOrDefault(var, -1) <= index) {
                toRelease.add(entry.getValue());
            }
        }
        for (final var reg : toRelease) {
            _unbindRegister(reg);
        }
    }

    private String _allocateForResult(IRVariable result, int index, List<IRVariable> operands, Set<String> protectedRegs) {
        final var existing = varToReg.get(result);
        if (existing != null) {
            return existing;
        }
        for (final var operand : operands) {
            if (operand == null || operand.equals(result)) {
                continue;
            }
            final var reg = varToReg.get(operand);
            if (reg != null && lastUse.getOrDefault(operand, -1) == index) {
                _unbindRegister(reg);
                _bindRegister(result, reg);
                return reg;
            }
        }
        return _allocateRegister(result, index, protectedRegs);
    }

    private String _findFreeRegister() {
        for (final var reg : freeRegs) {
            return reg;
        }
        return null;
    }

    private String _selectSpillRegister(int index, Set<String> protectedRegs) {
        String bestReg = null;
        int bestLastUse = -1;
        for (final var reg : REGISTERS) {
            if (protectedRegs.contains(reg)) {
                continue;
            }
            final var var = regToVar.get(reg);
            if (var == null) {
                return reg;
            }
            final var lu = lastUse.getOrDefault(var, -1);
            if (lu <= index) {
                return reg;
            }
            if (lu > bestLastUse) {
                bestLastUse = lu;
                bestReg = reg;
            }
        }
        return bestReg;
    }

    private void _spillRegister(String reg, IRVariable var) {
        final var label = spillLabels.computeIfAbsent(var, this::_createSpillLabel);
        _emit("    sw " + reg + ", " + label);
    }

    private String _createSpillLabel(IRVariable var) {
        final var raw = var.getName();
        final var sanitized = raw.replaceAll("[^A-Za-z0-9_]", "_");
        final var label = (var.isTemp() ? "tmp_" : "var_") + sanitized;
        dataLines.add(label + ": .word 0");
        return label;
    }

    private void _bindRegister(IRVariable var, String reg) {
        varToReg.put(var, reg);
        regToVar.put(reg, var);
        freeRegs.remove(reg);
    }

    private void _unbindRegister(String reg) {
        final var existing = regToVar.remove(reg);
        if (existing != null) {
            varToReg.remove(existing);
        }
        freeRegs.add(reg);
    }

    private String _ensureValueInRegister(IRValue value, int index, Set<String> protectedRegs) {
        if (value instanceof IRVariable var) {
            return _ensureInRegister(var, index, protectedRegs);
        }
        if (value instanceof IRImmediate immediate) {
            final var tempVar = IRVariable.temp();
            final var reg = _allocateRegister(tempVar, index, protectedRegs);
            _emit("    li " + reg + ", " + immediate.getValue());
            return reg;
        }
        throw new RuntimeException("Unknown IR value: " + value);
    }

    private List<IRVariable> _collectVars(IRValue lhs, IRValue rhs) {
        final var vars = new ArrayList<IRVariable>();
        if (lhs instanceof IRVariable var) {
            vars.add(var);
        }
        if (rhs instanceof IRVariable var) {
            vars.add(var);
        }
        return vars;
    }

    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        // TODO: 输出汇编代码到文件
        final var output = new ArrayList<String>();
        if (!dataLines.isEmpty()) {
            output.add(".data");
            output.addAll(dataLines);
        }
        output.add(".text");
        output.addAll(textLines);
        FileUtils.writeLines(path, output);
    }
}
