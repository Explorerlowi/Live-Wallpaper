#!/usr/bin/env python3
"""
数学计算器脚本
支持基础运算（加减乘除）和高级运算（幂、根、对数）
"""

import math
import sys
import json
from typing import Union


def calculate(expression: str) -> dict:
    """
    执行数学表达式计算
    
    参数:
        expression: 数学表达式字符串
        
    返回:
        包含结果或错误信息的字典
    """
    try:
        # 安全的数学函数命名空间
        safe_dict = {
            # 基础运算符已通过 eval 支持
            # 高级数学函数
            'sqrt': math.sqrt,      # 平方根
            'pow': pow,             # 幂运算
            'log': math.log,        # 自然对数
            'log10': math.log10,    # 以10为底的对数
            'log2': math.log2,      # 以2为底的对数
            'exp': math.exp,        # e的幂
            'abs': abs,             # 绝对值
            # 三角函数
            'sin': math.sin,
            'cos': math.cos,
            'tan': math.tan,
            'asin': math.asin,
            'acos': math.acos,
            'atan': math.atan,
            # 常数
            'pi': math.pi,
            'e': math.e,
            # 取整函数
            'ceil': math.ceil,
            'floor': math.floor,
            'round': round,
        }
        
        # 计算表达式
        result = eval(expression, {"__builtins__": {}}, safe_dict)
        
        return {
            "success": True,
            "result": result,
            "expression": expression
        }
        
    except ZeroDivisionError:
        return {
            "success": False,
            "error": "除以零错误",
            "expression": expression
        }
    except (ValueError, TypeError) as e:
        return {
            "success": False,
            "error": f"计算错误: {str(e)}",
            "expression": expression
        }
    except Exception as e:
        return {
            "success": False,
            "error": f"未知错误: {str(e)}",
            "expression": expression
        }


def format_result(result_dict: dict) -> str:
    """格式化输出结果"""
    if result_dict["success"]:
        result = result_dict["result"]
        expr = result_dict["expression"]
        
        # 格式化数字显示
        if isinstance(result, float):
            if result.is_integer():
                result_str = str(int(result))
            else:
                result_str = f"{result:.10g}"  # 最多10位有效数字
        else:
            result_str = str(result)
            
        return f"{expr} = {result_str}"
    else:
        return f"错误: {result_dict['error']}\n表达式: {result_dict['expression']}"


def main():
    """主函数"""
    if len(sys.argv) < 2:
        print("用法: python calculate.py <数学表达式>")
        print("\n支持的操作:")
        print("  基础运算: +, -, *, /, **, %")
        print("  高级函数: sqrt(), pow(), log(), log10(), log2(), exp()")
        print("  三角函数: sin(), cos(), tan(), asin(), acos(), atan()")
        print("  常数: pi, e")
        print("  其他: abs(), ceil(), floor(), round()")
        print("\n示例:")
        print("  python calculate.py \"2 + 3\"")
        print("  python calculate.py \"sqrt(16)\"")
        print("  python calculate.py \"pow(2, 10)\"")
        print("  python calculate.py \"log10(100)\"")
        print("  python calculate.py \"sin(pi/4)\"")
        sys.exit(1)
    
    # 获取表达式（可能包含多个参数，需要合并）
    expression = " ".join(sys.argv[1:])
    
    # 计算
    result = calculate(expression)
    
    # 输出格式化结果
    print(format_result(result))
    
    # 同时输出 JSON 格式（便于程序调用）
    if "--json" in sys.argv:
        print("\n--- JSON 输出 ---")
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
