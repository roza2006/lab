; 数据段：存储数组、计数器、提示信息
DATA SEGMENT
    ; 待统计的8位无符号数数组（十六进制）
    NUMBERS DB 12H, 35H, 46H, 79H, 0AH, 0FH, 22H, 57H
    ; 自动计算数组长度
    COUNT EQU $ - NUMBERS
    ; 奇数、偶数计数器，初始为0
    ODD_CNT DB 0
    EVEN_CNT DB 0
    ; 提示字符串（DOS输出结束标记$）
    MSG1 DB 'Odd Count: $'
    MSG2 DB 0AH, 0DH, 'Even Count: $'  ; 0AH=换行，0DH=回车
DATA ENDS

; 代码段：核心执行逻辑（全部使用 NEAR 过程）
CODE SEGMENT
    ASSUME CS:CODE, DS:DATA
START:
    MOV AX, DATA
    MOV DS, AX
    ; 调用奇偶数统计子程序（NEAR 类型）
    CALL COUNT_ODD_EVEN
    ; 调用结果输出子程序（NEAR 类型）
    CALL DISPLAY_RESULT
    ; 程序退出：DOS 21H 中断 4CH 号功能（主动退出，无需依赖 FAR RET）
    MOV AH, 4CH
    INT 21H

; 奇偶数统计子程序（NEAR 类型，同一段内调用）
COUNT_ODD_EVEN PROC NEAR
    PUSH AX    ; 保护现场
    PUSH BX
    PUSH CX
    PUSH SI

    MOV CX, COUNT  ; 循环计数器 = 数组长度
    MOV SI, 0      ; 数组索引初始化为0
NEXT_NUM:
    MOV AL, NUMBERS[SI]  ; 取出当前数组元素
    TEST AL, 01H         ; 最低位检测：判断奇偶（核心指令）
    JNZ ODD_NUMBER       ; ZF=0（最低位1），跳转到奇数处理

EVEN_NUMBER:
    INC EVEN_CNT         ; 偶数计数器+1
    JMP NEXT_STEP
ODD_NUMBER:
    INC ODD_CNT          ; 奇数计数器+1
NEXT_STEP:
    INC SI               ; 索引+1，指向下一个元素
    LOOP NEXT_NUM        ; CX-1，非0则继续循环

    POP SI     ; 恢复现场（与入栈顺序相反）
    POP CX
    POP BX
    POP AX
    RET
COUNT_ODD_EVEN ENDP

; 通用数字输出子程序（NEAR 类型：将 AL 中的数字转为 ASCII 并输出）
DISPLAY_SUB PROC 
    PUSH AX
    PUSH DX
    ADD AL, 30H           ; 数字 -> ASCII 码（0->30H='0'，9->39H='9'）
    MOV DL, AL
    MOV AH, 02H
    INT 21H               ; DOS中断：输出单个字符
    POP DX
    POP AX
    RET
DISPLAY_SUB ENDP

; 结果输出子程序（NEAR 类型）
DISPLAY_RESULT PROC 
    PUSH AX
    PUSH DX

    ; 输出奇数提示信息
    LEA DX, MSG1
    MOV AH, 09H
    INT 21H               ; DOS中断：输出字符串（以$结尾）
    ; 输出奇数个数
    MOV AL, ODD_CNT
    CALL DISPLAY_SUB

    ; 输出偶数提示信息（先换行回车）
    LEA DX, MSG2
    MOV AH, 09H
    INT 21H
    ; 输出偶数个数
    MOV AL, EVEN_CNT
    CALL DISPLAY_SUB

    POP DX
    POP AX
    RET
DISPLAY_RESULT ENDP

CODE ENDS
END START  ; 程序入口标记