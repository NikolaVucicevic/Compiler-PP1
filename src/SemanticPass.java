package rs.ac.bg.etf.pp1;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

/* =========================================================
 *                  SEMANTIČKI PROLAZ (SemanticPass)
 * ---------------------------------------------------------
 * - proverava tipove, deklaracije, opsege i strukturu koda
 * - implementira Visitor šablon
 * - koristi Tab (tabelu simbola)
 * ========================================================= */

public class SemanticPass extends VisitorAdaptor {

    // =======================================================
    // GLOBALNA POLJA I STANJA
    // =======================================================
    int printCallCount = 0;
    int varDeclCount = 0;
    Obj currentMethod = null;
    Obj currentClass = null;
    boolean returnFound = false;
    boolean errorDetected = false;
    int nVars;

    private Map<SyntaxNode, Struct> nizelementi = new HashMap<>();
    Struct currentListDecl;

    Logger log = Logger.getLogger(getClass());

    // =======================================================
    // FUNKCIJE ZA PRIJAVU GREŠAKA I INFO
    // =======================================================
    public void report_error(String message, SyntaxNode info) {
        errorDetected = true;
        StringBuilder msg = new StringBuilder(message);
        int line = (info == null) ? 0 : info.getLine();
        if (line != 0) msg.append(" na liniji ").append(line);
        log.error(msg.toString());
    }

    public void report_info(String message, SyntaxNode info) {
        StringBuilder msg = new StringBuilder(message);
        int line = (info == null) ? 0 : info.getLine();
        if (line != 0) msg.append(" na liniji ").append(line);
        log.info(msg.toString());
    }

    // =======================================================
    // SEKCIJA: DEKLARACIJE KONSTANTI
    // =======================================================
    public void visit(ConstDeclarationSingle varDecl) {
        SyntaxNode par = varDecl.getParent();
        while(!(par instanceof ConstDeclList)) par = par.getParent();
        currentListDecl = ((ConstDeclList) par).getType().struct;
        varDeclCount++;

        String name = varDecl.getConstName();
        if (Tab.currentScope().findSymbol(name) != null) {
            report_error("Greska: Ime '" + name + "' je već definisano u ovom opsegu!", varDecl);
            return;
        }
        if(currentClass!=null) {
            report_info("Deklarisan clan klase " + varDecl.getConstName(), varDecl);
            Obj varNode = Tab.insert(Obj.Fld, varDecl.getConstName(), currentListDecl);
            varNode.setFpPos(-1);
        } else {
            report_info("Deklarisana promenljiva " + varDecl.getConstName(), varDecl);
            Obj varNode = Tab.insert(Obj.Var, varDecl.getConstName(), currentListDecl);
            varNode.setFpPos(-1);
        }
    }

    public void visit(ConstDeclarationBetween varDecl) {
        varDeclCount++;
        String name = varDecl.getConstName();

        if (Tab.currentScope().findSymbol(name) != null) {
            report_error("Greska: Ime '" + name + "' je već definisano u ovom opsegu!", varDecl);
            return;
        }
        if(currentClass!=null) {
            report_info("Deklarisan clan klase " + varDecl.getConstName(), varDecl);
            Obj varNode = Tab.insert(Obj.Fld, varDecl.getConstName(), currentListDecl);
            varNode.setFpPos(-1);
        } else {
            report_info("Deklarisana promenljiva " + varDecl.getConstName(), varDecl);
            Obj varNode = Tab.insert(Obj.Var, varDecl.getConstName(), currentListDecl);
            varNode.setFpPos(-1);
        }
    }

    // =======================================================
    // SEKCIJA: DEKLARACIJE PROMENLJIVIH
    // =======================================================
    public void visit(VarDeclSingleList varDecl) {}

    public void visit(VarDeclBetween varDeclBetween) {
        varDeclCount++;
        String name = varDeclBetween.getVarName();
        if (Tab.currentScope().findSymbol(name) != null) {
            report_error("Greska: Ime '" + name + "' je već definisano u ovom opsegu!", varDeclBetween);
            return;
        }
        if (currentMethod != null) {
            report_info("Deklarisana lokalna promenljiva " + name, varDeclBetween);
            Tab.insert(Obj.Var, name, currentListDecl);
        } else if(currentClass!=null) {
            report_info("Deklarisana promenljiva " + varDeclBetween.getVarName(), varDeclBetween);
            Tab.insert(Obj.Fld, varDeclBetween.getVarName(), currentListDecl);
        } else {
            report_info("Deklarisan clan klase " + varDeclBetween.getVarName(), varDeclBetween);
            Tab.insert(Obj.Var, varDeclBetween.getVarName(), currentListDecl);
        }
    }

    public void visit(VarDeclSingle varDeclSingle) {
        SyntaxNode par = varDeclSingle.getParent();
        while(!(par instanceof VarDeclSingleList)) par = par.getParent();
        currentListDecl = ((VarDeclSingleList) par).getType().struct;
        varDeclCount++;

        String name = varDeclSingle.getVarName();
        if (Tab.currentScope().findSymbol(name) != null) {
            report_error("Greska: Ime '" + name + "' je već definisano u ovom opsegu!", varDeclSingle);
            return;
        }
        if (currentMethod != null) {
            report_info("Deklarisana lokalna promenljiva " + name, varDeclSingle);
            Tab.insert(Obj.Var, name, currentListDecl);
        } else if(currentClass!=null) {
            report_info("Deklarisana promenljiva " + varDeclSingle.getVarName(), varDeclSingle);
            Tab.insert(Obj.Fld, varDeclSingle.getVarName(), currentListDecl);
        } else {
            report_info("Deklarisan clan klase " + varDeclSingle.getVarName(), varDeclSingle);
            Tab.insert(Obj.Var, varDeclSingle.getVarName(), currentListDecl);
        }
    }

    public void visit(VarDeclArraySingle varDeclArraySingle) {
        varDeclCount++;
        SyntaxNode par = varDeclArraySingle.getParent();
        while(!(par instanceof VarDeclArrayList)) par = par.getParent();
        currentListDecl = ((VarDeclArrayList) par).getType().struct;
        String name = varDeclArraySingle.getVarName();

        if (Tab.currentScope().findSymbol(name) != null) {
            report_error("Greska: Ime '" + name + "' je već definisano!", varDeclArraySingle);
            return;
        }
        Struct arrayStruct = new Struct(Struct.Array, currentListDecl);

        if (currentMethod != null)
            Tab.insert(Obj.Var, name, arrayStruct);
        else if(currentClass!=null)
            Tab.insert(Obj.Fld, name, arrayStruct);
        else
            Tab.insert(Obj.Var, name, arrayStruct);
    }

    public void visit(VarDeclArrayBetween varDeclArrayBetween) {
        varDeclCount++;
        String name = varDeclArrayBetween.getVarName();
        if (Tab.currentScope().findSymbol(name) != null) {
            report_error("Greska: Ime '" + name + "' je već definisano!", varDeclArrayBetween);
            return;
        }
        Struct arrayStruct = new Struct(Struct.Array, currentListDecl);
        if (currentMethod != null)
            Tab.insert(Obj.Var, name, arrayStruct);
        else if(currentClass!=null)
            Tab.insert(Obj.Fld, name, arrayStruct);
        else
            Tab.insert(Obj.Var, name, arrayStruct);
    }

    // =======================================================
    // SEKCIJA: PARAMETRI FUNKCIJA
    // =======================================================
    public void visit(FormalParamDeclA formalParamDecl) {
        report_info("Deklarisan parametar " + formalParamDecl.getParamName(), formalParamDecl);
        Tab.insert(Obj.Var, formalParamDecl.getParamName(), formalParamDecl.getType().struct);
    }

    public void visit(FormalParamArray formalParamArray) {
        Struct elemType = formalParamArray.getType().struct;
        Struct arrayStruct = new Struct(Struct.Array, elemType);
        Tab.insert(Obj.Var, formalParamArray.getParamName(), arrayStruct);
    }

    public void visit(FormalParamDeclSet formalParamArray) {
        Struct elemType = Tab.intType;
        Struct arrayStruct = new Struct(Struct.Array, elemType);
        Obj varNode = Tab.insert(Obj.Var, formalParamArray.getParamName(), arrayStruct);
        varNode.setFpPos(5);
    }

    // =======================================================
    // SEKCIJA: PROGRAM I UGRADJENE FUNKCIJE
    // =======================================================
    public void visit(ProgName progName) {
        progName.obj = Tab.insert(Obj.Prog, progName.getProgName(), Tab.noType);
        Tab.openScope();

        // Ugradjene funkcije add, addAll, initMethod
        Obj addMethod = Tab.insert(Obj.Meth, "add", Tab.noType);
        addMethod.setLevel(2);
        addMethod.setAdr(Code.pc);
        Tab.openScope();
        Struct intArray = new Struct(Struct.Array, Tab.intType);
        Tab.insert(Obj.Var, "s1", intArray);
        Tab.insert(Obj.Var, "val", Tab.intType);
        Tab.insert(Obj.Var, "duz", Tab.intType);
        Tab.insert(Obj.Var, "i", Tab.intType);
        Tab.insert(Obj.Var, "length", Tab.intType);
        addMethod.setLocals(Tab.currentScope().getLocals());
        Tab.closeScope();

        Obj addAllMethod = Tab.insert(Obj.Meth, "addAll", Tab.noType);
        addAllMethod.setLevel(2);
        Tab.openScope();
        Struct intArray1 = new Struct(Struct.Array, Tab.intType);
        Tab.insert(Obj.Var, "s1", intArray1);
        Tab.insert(Obj.Var, "niz", intArray1);
        Tab.insert(Obj.Var, "length", Tab.intType);
        Tab.insert(Obj.Var, "i", Tab.intType);
        addAllMethod.setLocals(Tab.currentScope().getLocals());
        Tab.closeScope();

        Obj initMethod = Tab.insert(Obj.Meth, "initMethod", Tab.noType);
        initMethod.setLevel(0);
        Tab.openScope();
        initMethod.setLocals(Tab.currentScope().getLocals());
        Tab.closeScope();
    }

    public void visit(Program program) {
        nVars = Tab.currentScope.getnVars();
        Tab.chainLocalSymbols(program.getProgName().obj);
        Tab.closeScope();
    }

    // =======================================================
    // SEKCIJA: KLASE I INTERFEJSI
    // =======================================================
    public void visit(InterfaceNameSingle interName) {
        Struct interStruct = new Struct(Struct.Interface);
        interName.obj = Tab.insert(Obj.Type, interName.getInterfaceName(), interStruct);
        currentClass = interName.obj;
        Tab.openScope();
        Obj newThis = Tab.insert(Obj.Fld, "TVF", Tab.intType);
        newThis.setAdr(0);
        newThis.setLevel(1);
    }

    public void visit(ClassNameSingle className) {
        Struct classStruct = new Struct(Struct.Class);
        className.obj = Tab.insert(Obj.Type, className.getClassName(), classStruct);
        currentClass = className.obj;
        Tab.openScope();
        Obj newThis = Tab.insert(Obj.Fld, "TVF", Tab.intType);
        newThis.setAdr(0);
        newThis.setLevel(1);
    }

    public void visit(ClassNameExtendsError className) {
        Struct classStruct = new Struct(Struct.Class);
        className.obj = Tab.insert(Obj.Type, className.getClassName(), classStruct);
        currentClass = className.obj;
        Tab.openScope();
        Obj newThis = Tab.insert(Obj.Fld, "TVF", Tab.intType);
        newThis.setAdr(0);
        newThis.setLevel(1);
    }

    public void visit(ClassNameExtends className) {
        Obj baseClassObj = Tab.find(className.getBaseClass());
        Struct baseStruct = baseClassObj.getType();
        Struct classStruct = new Struct(Struct.Class, baseStruct);
        classStruct.setElementType(baseStruct);
        Obj classObj = Tab.insert(Obj.Type, className.getClassName(), classStruct);
        className.obj = classObj;
        currentClass = classObj;
        Tab.openScope();

        for (Obj member : baseStruct.getMembers()) {
            Obj copy = Tab.insert(member.getKind(), member.getName(), member.getType());
            copy.setAdr(member.getAdr());
            copy.setLevel(member.getLevel());
            if (member.getKind() == Obj.Meth) {
                Tab.openScope();
                for (Obj local : member.getLocalSymbols()) {
                    if (local.getName().equals("this")) {
                        Obj newThis = Tab.insert(Obj.Var, "this", currentClass.getType());
                        newThis.setAdr(0);
                        newThis.setLevel(1);
                    }
                    Obj newLocal = Tab.insert(local.getKind(), local.getName(), local.getType());
                    newLocal.setAdr(local.getAdr());
                    newLocal.setLevel(local.getLevel());
                }
                Tab.chainLocalSymbols(copy);
                Tab.closeScope();
            }
        }
    }

    public void visit(ClassDeclaration classDecl) {
        Tab.chainLocalSymbols(classDecl.getClassName().obj.getType());
        Tab.closeScope();
        currentClass = null;
    }

    public void visit(InterfaceDeclaration interDecl) {
        Tab.chainLocalSymbols(interDecl.getInterfaceName().obj.getType());
        Tab.closeScope();
        currentClass = null;
    }

    // =======================================================
    // SEKCIJA: TIPOVI
    // =======================================================
    public void visit(Type type) {
        Obj typeNode = Tab.find(type.getTypeName());
        if (typeNode == Tab.noObj) {
            report_error("Nije pronadjen tip " + type.getTypeName() + " u tabeli simbola ", null);
            type.struct = Tab.noType;
        } else {
            if (Obj.Type == typeNode.getKind()) {
                type.struct = typeNode.getType();
            } else {
                report_error("Ime " + type.getTypeName() + " ne predstavlja tip!", type);
                type.struct = Tab.noType;
            }
        }
    }

    // =======================================================
    // SEKCIJA: METODE I FUNKCIJE
    // =======================================================
    public void visit(MethodTypeNameA methodTypeName) {
        currentMethod = Tab.insert(Obj.Meth, methodTypeName.getMethName(), methodTypeName.getType().struct);
        methodTypeName.obj = currentMethod;
        Tab.openScope();
        if (currentClass != null) Tab.insert(Obj.Var, "this", currentClass.getType());
        report_info("Obradjuje se funkcija " + methodTypeName.getMethName(), methodTypeName);
    }

    public void visit(VoidMethodTypeName methodTypeName) {
        currentMethod = Tab.insert(Obj.Meth, methodTypeName.getMethName(), Tab.noType);
        methodTypeName.obj = currentMethod;
        Tab.openScope();
        if (currentClass != null) Tab.insert(Obj.Var, "this", currentClass.getType());
        report_info("Obradjuje se void funkcija " + methodTypeName.getMethName(), methodTypeName);
    }

    public void visit(MethodDecl methodDecl) {
        Tab.chainLocalSymbols(currentMethod);
        Tab.closeScope();
        returnFound = false;
        currentMethod = null;
    }

    public void visit(DefaultMethodDecl methodDecl) {
        if(!returnFound && currentMethod.getType() != Tab.noType)
            report_error("Funkcija " + currentMethod.getName() + " nema return iskaz!", methodDecl);
        Tab.chainLocalSymbols(currentMethod);
        Tab.closeScope();
        returnFound = false;
        currentMethod = null;
    }

    public void visit(AbstractMethodDecl methodDecl) {
        Tab.chainLocalSymbols(currentMethod);
        Tab.closeScope();
        returnFound = false;
        currentMethod = null;
    }

    // =======================================================
    // SEKCIJA: DESIGNATORI I PRISTUP POLJIMA
    // =======================================================
    public void visit(DesignatorIdent designator) {
        Obj obj = Tab.find(designator.getName());
        if (obj == Tab.noObj)
            report_error("Ime " + designator.getName() + " nije deklarisano!", designator);
        designator.obj = obj;
    }

    public void visit(ArrayDesignator arrayDesignator) {
        Obj obj = Tab.find(arrayDesignator.getName());
        if (obj == Tab.noObj) {
            report_error("Ime " + arrayDesignator.getName() + " nije deklarisano!", arrayDesignator);
            arrayDesignator.obj = Tab.noObj;
            return;
        }
        if (obj.getType().getKind() != Struct.Array) {
            report_error("Identifikator " + arrayDesignator.getName() + " nije niz!", arrayDesignator);
            arrayDesignator.obj = Tab.noObj;
            return;
        }
        arrayDesignator.obj = obj;
        nizelementi.put(arrayDesignator, obj.getType().getElemType());
    }

    public void visit(FieldAccess access) {
        Obj baseObj = access.getDesignator().obj;
        if (baseObj == null || baseObj == Tab.noObj) {
            report_error("Levi operand u pristupu članu objekta nije validan.", access);
            access.obj = Tab.noObj;
            return;
        }

        if (baseObj.getType().getKind() != Struct.Class && baseObj.getType().getKind() != Struct.Interface) {
            if (baseObj.getType().getKind() == Struct.Array) {
                Struct st = nizelementi.get(access.getDesignator());
                if (st == null || (st.getKind() != 4 && st.getKind() != 7)) {
                    report_error("Levi operand nije objekat klase!", access);
                    access.obj = Tab.noObj;
                    return;
                }
            } else {
                report_error("Levi operand nije objekat klase!", access);
                access.obj = Tab.noObj;
                return;
            }
        }

        Struct oleja = nizelementi.get(access.getDesignator());
        Obj member = null;
        if (baseObj.getType().getKind() != Struct.Array) {
            for (Obj o : baseObj.getType().getMembers())
                if (o.getName().equals(access.getFieldName())) { member = o; break; }
        } else {
            for (Obj o : oleja.getMembers())
                if (o.getName().equals(access.getFieldName())) { member = o; break; }
        }

        if (access.getDesignator() instanceof DesignatorIdent &&
            ((DesignatorIdent)access.getDesignator()).getName().equals("this")) {
            Scope roditeljski = Tab.currentScope().getOuter();
            if (roditeljski != null) {
                for (Obj obj : roditeljski.values())
                    if (obj.getName().equals(access.getFieldName())) { member = obj; break; }
            }
        }

        if (member == null || member == Tab.noObj) {
            report_error("Član '" + access.getFieldName() + "' ne postoji u klasi!", access);
            access.obj = Tab.noObj;
            return;
        }

        access.obj = member;
    }

    // =======================================================
    // SEKCIJA: POZIVI FUNKCIJA I PRINT
    // =======================================================
    public void visit(PrintStmtExprOnly print) {
        Expr expr = print.getExpr();
        if (expr instanceof TermExpr) {
            Term term = ((TermExpr) expr).getTerm();
            if (term instanceof SingleFactor) {
                Factor factor = ((SingleFactor) term).getFactor();
                if (factor instanceof Var) {
                    Designator designator = ((Var) factor).getDesignator();
                    if (designator instanceof ArrayDesignator) printCallCount++;
                }
            }
        }
        printCallCount++;
    }

    public void visit(FuncCall funcCall) {
        Obj func = funcCall.getDesignator().obj;
        if (Obj.Meth == func.getKind()) {
            if (Tab.noType == func.getType())
                report_error("Funkcija " + func.getName() + " nema povratnu vrednost!", funcCall);
            else {
                report_info("Poziv funkcije " + func.getName(), funcCall);
                funcCall.struct = func.getType();
            }
        } else {
            report_error("Ime " + func.getName() + " nije funkcija!", funcCall);
            funcCall.struct = Tab.noType;
        }
    }

    // =======================================================
    // SEKCIJA: IZRAZI I OPERACIJE
    // =======================================================
    public void visit(MulopTerm term) {
        Struct left = term.getTerm().struct;
        Struct right = term.getFactor().struct;
        boolean leftValid = (left == Tab.intType);
        boolean rightValid = (right == Tab.intType);

        if (left.getKind() == Struct.Array) {
            Struct str = nizelementi.get(term.getTerm());
            if (str == Tab.intType) leftValid = true;
        }
        if (right.getKind() == Struct.Array) {
            Struct str = nizelementi.get(term.getFactor());
            if (str == Tab.intType) rightValid = true;
        }

        if (leftValid && rightValid) term.struct = Tab.intType;
        else {
            report_error("Operandi u množenju moraju biti int ili elementi niza.", term);
            term.struct = Tab.noType;
        }
    }

    public void visit(SingleFactor term){
        term.struct = term.getFactor().struct;
        Struct str = nizelementi.get(term.getFactor());
        if (str != null) nizelementi.put(term, str);
    }

    public void visit(TermExpr termExpr){
        termExpr.struct = termExpr.getTerm().struct;
        Struct str = nizelementi.get(termExpr.getTerm());
        if (str != null) nizelementi.put(termExpr, str);
    }

    public void visit(NegExpr negExpr){
        Struct termType = negExpr.getTerm().struct;
        if (termType == Tab.intType) negExpr.struct = Tab.intType;
        else {
            report_error("Unarni minus se moze primeniti samo na int tip.", negExpr);
            negExpr.struct = Tab.noType;
        }
    }

    public void visit(AddExpr addExpr) {
        Struct left = addExpr.getExpr().struct;
        Struct right = addExpr.getTerm().struct;
        boolean leftValid = (left == Tab.intType);
        boolean rightValid = (right == Tab.intType);

        if (left.getKind() == Struct.Array) {
            Struct str = nizelementi.get(addExpr.getExpr());
            if (str == Tab.intType) leftValid = true;
        }
        if (right.getKind() == Struct.Array) {
            Struct str = nizelementi.get(addExpr.getTerm());
            if (str == Tab.intType) rightValid = true;
        }

        if (leftValid && rightValid) addExpr.struct = Tab.intType;
        else {
            report_error("Sabiranje dozvoljeno samo za int vrednosti i elemente niza int.", addExpr);
            addExpr.struct = Tab.noType;
        }
    }

    public void visit(MapExpr mapExpr) {
        Obj funcObj = mapExpr.getDesignator().obj;
        Obj arrObj = mapExpr.getDesignator1().obj;
        Setop setop = mapExpr.getSetop();
        if (setop instanceof SetUnion) return;

        if (funcObj == Tab.noObj || funcObj.getKind() != Obj.Meth) {
            report_error("Levi operand operatora 'map' mora biti funkcija!", mapExpr);
            mapExpr.struct = Tab.noType;
            return;
        }
        if (arrObj == Tab.noObj || arrObj.getType().getKind() != Struct.Array) {
            report_error("Desni operand operatora 'map' mora biti niz!", mapExpr);
            mapExpr.struct = Tab.noType;
            return;
        }

        Struct elemType = arrObj.getType().getElemType();
        Collection<Obj> locals = funcObj.getLocalSymbols();
        Obj param = locals.iterator().next();
        if (!elemType.assignableTo(param.getType())) {
            report_error("Tip elementa niza nije dodeljiv parametru funkcije!", mapExpr);
            mapExpr.struct = Tab.noType;
            return;
        }
        mapExpr.struct = funcObj.getType();
    }

    // =======================================================
    // SEKCIJA: FAKTORI I KONSTANTE
    // =======================================================
    public void visit(ConstNum constNum){ constNum.struct = Tab.intType; }
    public void visit(ConstChar constChar){ constChar.struct = Tab.charType; }
    public void visit(ConstBool constBool){ constBool.struct = Tab.find("bool").getType(); }

    public void visit(Var var){
        var.struct = var.getDesignator().obj.getType();
        Struct str = nizelementi.get(var.getDesignator());
        if (str != null) nizelementi.put(var, str);
    }

    public void visit(ParenExpr factor) {
        Expr expr = factor.getExpr();
        factor.struct = expr.struct;
        if (expr instanceof TermExpr) {
            Term term = ((TermExpr) expr).getTerm();
            if (term instanceof SingleFactor) {
                Factor inner = ((SingleFactor) term).getFactor();
                if (inner instanceof Var) {
                    Designator designator = ((Var) inner).getDesignator();
                    if (designator instanceof ArrayDesignator) {
                        Struct elemType = designator.obj.getType().getElemType();
                        factor.struct = elemType;
                    }
                }
            }
        }
    }

    public void visit(NewArray factor) {
        if (!factor.getExpr().struct.equals(Tab.intType))
            report_error("Izraz u 'new' mora biti tipa int!", factor);
        factor.struct = new Struct(Struct.Array, factor.getType().struct);
    }

    public void visit(NewSet factor) {
        if (!factor.getExpr().struct.equals(Tab.intType))
            report_error("Izraz u 'new set' mora biti tipa int!", factor);
        factor.struct = new Struct(Struct.Array, Tab.intType);
    }

    public void visit(NewClass newClass) {
        if (newClass.getType().struct.getKind() != Struct.Class) {
            report_error("Tip nakon 'new' mora biti klasa!", newClass);
            newClass.struct = Tab.noType;
        } else newClass.struct = newClass.getType().struct;
    }

    // =======================================================
    // SEKCIJA: RETURN ISKAZI
    // =======================================================
    public void visit(ReturnExpr returnExpr){
        returnFound = true;
        Struct currMethType = currentMethod.getType();
        if(returnExpr.getExpr().struct.getKind() == Struct.Array) {
            if(!currMethType.compatibleWith(nizelementi.get(returnExpr.getExpr())))
                report_error("Tip izraza u return ne slaze se sa tipom niza " + currentMethod.getName(), returnExpr);
        } else if(!currMethType.compatibleWith(returnExpr.getExpr().struct))
            report_error("Tip izraza u return ne slaze se sa tipom povratne vrednosti! " + currentMethod.getName(), returnExpr);
    }

    public void visit(ReturnNoExpr returnNoExpr){
        returnFound = true;
        Struct currMethType = currentMethod.getType();
        if (!currMethType.equals(Tab.noType))
            report_error("Funkcija nije void, a koristi se return bez izraza!", returnNoExpr);
    }

    // =======================================================
    // SEKCIJA: USLOVI (BOOL IZRAZI)
    // =======================================================
    public void visit(OnlyExprCond condFact) {
        condFact.struct = condFact.getExpr().struct;
        boolean moze = condFact.struct.equals(Tab.find("bool").getType());
        Struct str = nizelementi.get(condFact.getExpr());
        if (str != null && str.equals(Tab.find("bool").getType())) moze = true;
        if (!moze) report_error("Uslov mora biti tipa bool!", condFact);
    }

    public void visit(RelationalCond condFact) {
        condFact.struct = Tab.find("bool").getType();
    }

    public void visit(SingleCondFact term) {
        term.struct = term.getCondFact().struct;
        Struct str = nizelementi.get(term.getCondFact());
        if (str != null) nizelementi.put(term, str);
    }

    public void visit(AndCondTerm term) {
        Struct left = term.getCondFact().struct;
        Struct right = term.getCondTerm().struct;
        boolean leftValid = left == Tab.find("bool").getType();
        boolean rightValid = right == Tab.find("bool").getType();
        if (left.getKind() == Struct.Array)
            if (nizelementi.get(term.getCondFact()) == Tab.find("bool").getType()) leftValid = true;
        if (right.getKind() == Struct.Array)
            if (nizelementi.get(term.getCondTerm()) == Tab.find("bool").getType()) rightValid = true;
        if (leftValid && rightValid) term.struct = Tab.find("bool").getType();
        else {
            report_error("AND uslovi moraju biti bool!", term);
            term.struct = Tab.noType;
        }
    }

    public void visit(SingleCondTerm term) {
        term.struct = term.getCondTerm().struct;
        Struct str = nizelementi.get(term.getCondTerm());
        if (str != null) nizelementi.put(term, str);
    }

    public void visit(OrCondition term) {
        Struct left = term.getCondTerm().struct;
        Struct right = term.getCondition().struct;
        boolean leftValid = left == Tab.find("bool").getType();
        boolean rightValid = right == Tab.find("bool").getType();
        if (left.getKind() == Struct.Array)
            if (nizelementi.get(term.getCondTerm()) == Tab.find("bool").getType()) leftValid = true;
        if (right.getKind() == Struct.Array)
            if (nizelementi.get(term.getCondition()) == Tab.find("bool").getType()) rightValid = true;
        if (leftValid && rightValid) term.struct = Tab.find("bool").getType();
        else {
            report_error("OR uslovi moraju biti bool!", term);
            term.struct = Tab.noType;
        }
    }

    // =======================================================
    // SEKCIJA: SET DEKLARACIJE
    // =======================================================
    public void visit(SetDeclSingle setDeclSingle) {
        varDeclCount++;
        Struct elemType = Tab.intType;
        Struct arrayStruct = new Struct(Struct.Array, elemType);
        if (currentMethod != null)
            Tab.insert(Obj.Var, setDeclSingle.getVarName(), arrayStruct);
        else if(currentClass!=null)
            Tab.insert(Obj.Fld, setDeclSingle.getVarName(), arrayStruct);
        else
            Tab.insert(Obj.Var, setDeclSingle.getVarName(), arrayStruct);
    }

    public void visit(SetDeclBetween setDeclBetween) {
        varDeclCount++;
        Struct elemType = Tab.intType;
        Struct arrayStruct = new Struct(Struct.Array, elemType);
        if (currentMethod != null)
            Tab.insert(Obj.Var, setDeclBetween.getVarName(), arrayStruct);
        else if(currentClass!=null)
            Tab.insert(Obj.Fld, setDeclBetween.getVarName(), arrayStruct);
        else
            Tab.insert(Obj.Var, setDeclBetween.getVarName(), arrayStruct);
    }

    // =======================================================
    // SEKCIJA: ZAVRŠNA PROVERA
    // =======================================================
    public boolean passed() {
        return !errorDetected;
    }
}
