---
name: math-calculator
description: Perform mathematical calculations including basic operations (addition, subtraction, multiplication, division) and advanced operations (powers, roots, logarithms). Use when the user needs to: (1) Calculate mathematical expressions, (2) Perform basic arithmetic, (3) Compute powers, roots, or logarithms, (4) Evaluate mathematical formulas, or any other mathematical computation tasks.
---

# 数学计算器技能

本技能提供基础和高级数学运算能力，支持纯文本输出计算结果。

## 支持的运算

### 基础运算
- **加法**: `+`
- **减法**: `-`
- **乘法**: `*`
- **除法**: `/`
- **幂运算**: `**` 或 `pow(x, y)`
- **取模**: `%`

### 高级运算
- **平方根**: `sqrt(x)`
- **幂运算**: `pow(base, exponent)`
- **自然对数**: `log(x)`
- **常用对数**: `log10(x)`
- **二进制对数**: `log2(x)`
- **指数函数**: `exp(x)` (e^x)
- **绝对值**: `abs(x)`

### 三角函数
- `sin(x)`, `cos(x)`, `tan(x)`
- `asin(x)`, `acos(x)`, `atan(x)`

### 常数
- **圆周率**: `pi` (π ≈ 3.14159...)
- **自然常数**: `e` (e ≈ 2.71828...)

### 取整函数
- **向上取整**: `ceil(x)`
- **向下取整**: `floor(x)`
- **四舍五入**: `round(x)`

## 使用方法

### 方式 1: 使用 Python 脚本（推荐）

对于复杂的数学运算，使用提供的 `scripts/calculate.py` 脚本：

```bash
python skills/math-calculator/scripts/calculate.py "<表达式>"
```

**示例**:

```bash
# 基础运算
python skills/math-calculator/scripts/calculate.py "2 + 3"
python skills/math-calculator/scripts/calculate.py "10 * 5 - 8"
python skills/math-calculator/scripts/calculate.py "100 / 4"

# 高级运算
python skills/math-calculator/scripts/calculate.py "sqrt(16)"
python skills/math-calculator/scripts/calculate.py "pow(2, 10)"
python skills/math-calculator/scripts/calculate.py "log10(1000)"
python skills/math-calculator/scripts/calculate.py "log2(128)"

# 三角函数
python skills/math-calculator/scripts/calculate.py "sin(pi/4)"
python skills/math-calculator/scripts/calculate.py "cos(0)"

# 复杂表达式
python skills/math-calculator/scripts/calculate.py "sqrt(pow(3, 2) + pow(4, 2))"
python skills/math-calculator/scripts/calculate.py "(2 + 3) * 4 - sqrt(16)"
```

**输出格式**: `<表达式> = <结果>`

**错误处理**: 脚本会捕获并报告计算错误（如除以零、无效参数等）。

### 方式 2: 直接生成 Python 代码

对于简单运算，可以直接生成 Python 代码执行：

```python
import math

# 基础运算
result = 2 + 3  # 5

# 高级运算
result = math.sqrt(16)  # 4.0
result = pow(2, 10)  # 1024
result = math.log10(100)  # 2.0
result = math.log(math.e)  # 1.0

# 复杂表达式
result = (2 + 3) * 4 - math.sqrt(16)  # 16.0

print(f"结果: {result}")
```

## 响应格式

当用户请求数学计算时：

1. **识别运算类型**：确定是基础运算还是高级运算
2. **选择合适方式**：
   - 简单表达式：直接计算并输出结果
   - 复杂表达式：使用 `calculate.py` 脚本
3. **输出结果**：以纯文本格式显示计算结果

**输出示例**:

```
计算: 2 + 3 * 4
结果: 14

计算: sqrt(pow(3, 2) + pow(4, 2))
结果: 5.0
解释: 这是计算直角三角形的斜边长度（勾股定理）
```

## 注意事项

1. **角度与弧度**：三角函数使用弧度制，需要时使用 `pi` 进行转换
2. **精度**：浮点数结果保留适当的有效数字
3. **错误处理**：自动捕获并报告除以零、负数开平方根等错误
4. **表达式安全**：脚本限制了可用的函数，仅允许安全的数学运算

## 常见使用场景

- "计算 25 的平方根"
- "2 的 10 次方是多少"
- "计算 log10(1000)"
- "求 sin(π/4) 的值"
- "100 除以 3 等于多少"
- "计算 (5 + 3) * 2 - 4"
