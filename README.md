# 🎯 PP1 Project – Mikrojava Compiler

This repository contains my project for the **Compilers (PP1)** course at the School of Electrical Engineering, University of Belgrade.  
The goal of this project is to implement a **compiler for the Mikrojava language**, supporting all stages of compilation: lexical, syntax, and semantic analysis, as well as code generation.

---

## 📁 Project Structure

```
ProjekatPP1/
 ├── spec/      → Lexical and syntax specifications (.lex, .cup)
 ├── src/       → Source code (Java files – analyzers, semantics, code generator)
 ├── test/      → Test programs (.mj files written in Mikrojava)
 ├── README.md  → Project description
 └── .gitignore → Ignored files (IDE configs, .class, out, build, etc.)
```

- **spec/** – contains `mjlexer.lex` and `mjparser.cup` which define the lexical tokens and grammar rules.  
- **src/** – implementation of the semantic analyzer (`SemanticPass`), code generator (`CodeGenerator`), and supporting classes (`SyntaxTreePrinter`, new AST node types, etc.).  
- **test/** – sample Mikrojava programs used to test functionality for levels A, B, and C.

---

## ⚙️ Compiler Phases

1. **Lexical Analysis**  
   - Implemented using **JFlex**.  
   - Recognizes tokens such as keywords, identifiers, constants, and operators.

2. **Syntax Analysis**  
   - Implemented using **CUP** parser generator.  
   - Builds the syntax tree and verifies grammatical correctness.

3. **Semantic Analysis**  
   - Implemented in the `SemanticPass.java` class.  
   - Validates type rules, variable declarations, and semantic correctness of expressions and statements.

4. **Code Generation**  
   - Implemented in `CodeGenerator.java`.  
   - Produces bytecode executable by the **Mikrojava virtual machine**.

---

## 🧩 Project Levels

### 🔹 **LEVEL A – Basic Constructs, Arrays, and Sets**

This level includes implementation of all basic program constructs such as assignments, arithmetic expressions, and calls to predefined functions.  
Support is added for arrays of primitive types and **sets of integers**, including union operations between sets.  
Programs must include a `main` function, as well as global and local variables (both primitive and array types).

### 🔹 **LEVEL B – Control Structures and Functions**

This level extends Level A with implementation of control structures (`if`, `else`, `do-while`, `break`, `continue`), return statements, and global method calls.  
It also introduces conditional and logical expressions (`&&`, `||`) and function calls with parameters.  
This level enables structured programming with nested blocks and correct scoping rules.

### 🔹 **LEVEL C – Object-Oriented Extensions**

Level C adds full **object-oriented programming (OOP)** support to Mikrojava.  
It implements class inheritance, object and array instantiation, interfaces with default methods, virtual function tables (VFT), and **polymorphic method calls**.  
Additionally, **substitution** is supported — allowing derived class objects to be passed where base class or interface references are expected.

---

## 🚀 Running the Project

1. Generate analyzers:
   ```bash
   java -jar tools/JFlex.jar spec/mjlexer.lex
   java -jar tools/java-cup-11b.jar -parser MJParser -symbols sym spec/mjparser.cup
   ```

2. Compile all Java files:
   ```bash
   javac -cp .;tools/java-cup-11b-runtime.jar src/**/*.java
   ```

3. Run a test program:
   ```bash
   java -cp .;tools/java-cup-11b-runtime.jar rs.etf.pp1.MJParser test/test1.mj
   ```

If compilation succeeds, an `.obj` file is generated and can be executed using the **Mikrojava emulator**.

---

## 📌 Notes

- The project is based on the official **ETF PP1 skeleton**, but the original skeleton files are **not included** in this repository.  
- Only classes and implementations that I developed are part of this project.  
- Main modifications and extensions are implemented in:
  - `SemanticPass.java`
  - `CodeGenerator.java`
  - additional AST node types (if used)
- Test programs were manually created to cover all functionalities for levels A, B, and C.

---

## 👨‍💻 Author

**Nikola Vučićević**  
Software Engineering Department  
School of Electrical Engineering, University of Belgrade  
Year: 2025
