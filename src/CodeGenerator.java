/**
 * CodeGenerator.java
 * Autor: Nikola Vučićević (ETF, Beograd)
 * Opis: Generiše bajtkod za MicroJava ekstenziju sa podrškom za nizove, setove i ugradjene funkcije.
 * Sadrži ugradjene metode add(), addAll() i initMethod(), kao i generisanje izraza, naredbi i kontrolnih struktura.
 */

package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.CounterVisitor.FormParamCounter;


import rs.ac.bg.etf.pp1.CounterVisitor.VarCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.LoopStartFinder;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Array;
import java.util.AbstractMap;

public class CodeGenerator extends VisitorAdaptor{

	
	private int mainPc;
	private Obj currentClass = null;
	private Obj methodType = null;
	private boolean prvaFunc = false;
	private Struct currentListDecl;
	private String programName;
	
	// uslovni skokovi (false jump npr. za if, do-while)
	private Map<SyntaxNode, Integer> falseJumpMap = new HashMap<>();

	// obični bezuslovni skokovi (npr. jmp preko else-a, skok na continue, break)
	private Map<SyntaxNode, Integer> jumpMap = new HashMap<>();
	
	//za sve potomke cvorove cuvamo koji predak treba da ga obradi
	private Map<SyntaxNode, List<SyntaxNode>> obradi = new HashMap<>();
	
	// koristimo da znamo gde se iskace iz while petlje
	private Map<SyntaxNode, Integer> dowhiles = new HashMap<>();
	
	// Mapa adresa funkcija: (klasa, metoda) → adresa početka koda
	Map<Map.Entry<String, String>, Integer> adreseFunkcija = new HashMap<>();
	
	//Tabela virtuelnih funkcija (TVF) za svaku klasu: ime klase → adresa TVF
	private Map<String, Integer> TVFKlase = new HashMap<>();
	
	// Tipovi elemenata nizova: ime niza → tip elemenata
	private Map<String, Struct> nizelementi = new HashMap<>();
	
	//Indeksi u data segmentu za statičke konstante
	List<Integer> constBrojevi = new ArrayList<>();
	
	//Vrednosti deklarisanih konstanti
	List<Integer> constVals = new ArrayList<>();
	
	
	//Flag koji označava da se trenutno alocira novi objekat (`new`)
	private boolean newJeSad = false;
	//Ime klase koja se trenutno instancira (`new`)
	private String NovaKlasa = "";
	
	
	/**
	 * Pomoćne statičke adrese u data segmentu.
	 * Koriste se kao privremene promenljive prilikom generisanja koda
	 */
	private int pomStat1 = -1;
	private int pomStat2 = -1;
	private int pomStat3 = -1;
	
	private int initFunc=-1;
	
	public int getMainPc() {
		return mainPc;
	}
	/**
	 * Obrada dece čvora – fiksira sve skokove (falseJump i jump)
	 * koji su bili privremeno ostavljeni za kasniju obradu.
	 * Svaki čvor se obrađuje samo jednom.
	 */
	
	public void obradiDecu(SyntaxNode node) {
		List<SyntaxNode> zaObradu = obradi.get(node);//obrada obe mape
	    if (zaObradu != null) {
	        for (SyntaxNode noda : zaObradu) {
	            Integer addr = falseJumpMap.get(noda);
	            if (addr != null) {
	                Code.fixup(addr); // skok na kraj petlje
	            }
	        }
	        obradi.remove(node); // obradi samo jednom
	    }
	    
	    if (zaObradu != null) {
	        for (SyntaxNode noda : zaObradu) {
	            Integer addr = jumpMap.get(noda);
	            if (addr != null) {
	                Code.fixup(addr); // skok na kraj petlje
	            }
	        }
	        obradi.remove(node); // obradi samo jednom
	    }
	}
	
	/**
	 * Proverava da li se čvor nalazi unutar do-while petlje.
	 * Ako jeste, čuva adresu početka petlje u mapi `dowhiles`
	 * radi generisanja povratnog skoka.
	 */
	
	public void daLiDoWhile(SyntaxNode node) {
		//idemo kroz cvorove pretke da nadjemo gde while treba da skoci
		SyntaxNode sn = node;
		while(sn!=null) {
			sn=sn.getParent();
			if((sn instanceof DoWhileStmt) || (sn instanceof DoWhileStmtExtra) || (sn instanceof DoWhileEmpty)) {
				if(dowhiles.get(sn)==null) {
					dowhiles.put(sn, Code.pc);
				}else {
					if(dowhiles.get(sn)>Code.pc) {
						dowhiles.put(sn, Code.pc);
					}
				}
				break;
			}
		}
	}
	
	/**
	 * Utvrđuje tip roditeljskog if-a (Matched/Unmatched) i
	 * postavlja odgovarajući skok (jump) koji treba da se
	 * fiksira nakon generisanja else grane.
	 */
	
	public void kojiIfIliMatch(SyntaxNode node) {
		SyntaxNode parent = node.getParent();
		
		if (parent instanceof UnmatchedIfElse) {
	        // Ovo je "then" u if (...) stmt1 else stmt2
	        Code.putJump(0);                     // preskoči else
	        jumpMap.put(node, Code.pc - 2);
	        obradi.computeIfAbsent(parent, k -> new ArrayList<>()).add(node);
	    }
	    
	    if (parent instanceof MatchedStatement && ((MatchedStatement) parent).getMatched() == node) {
	        // Ovo je "then" u if (...) stmt1 else stmt2
	        Code.putJump(0);
	        jumpMap.put(node, Code.pc - 2);
	        obradi.computeIfAbsent(parent, k -> new ArrayList<>()).add(node);
	    }
	}
	
	
	/** =========================================================
	 *                     GLAVNI PROGRAM
	 * ========================================================= */	

	
	public void visit(Program program) {
    	
    	
    }
	
	
	public void visit(ProgName progName) {
		programName=progName.getProgName();
    	
    	Obj addMethod = Tab.find("add");
    	addMethod.setAdr(Code.pc); // adresa funkcije u bajtkodu

    	generateBuiltinFunctions();
    	
    	Obj addAllMethod = Tab.find("addAll");
    	addAllMethod.setAdr(Code.pc); // adresa funkcije u bajtkodu
    	generateBuiltinFunctionsAddAll();
    	
    	
    }
	
	
	
	/** =========================================================
	 *                     DEKLARACIJE
	 * ---------------------------------------------------------
	 * Varijable, klase, konstante, interfejsi
	 * ========================================================= */
	
	
	
	/** ---------------- VARIJABLE ---------------- */
	
	// =======================================================
	// SEKCIJA: DEKLARACIJE VARIJABLI I SETOVA
	// =======================================================

	/**
	 * Globalna deklaracija jedne promenljive.
	 * Inicijalizuje promenljivu na 0 u statičkom segmentu memorije.
	 */
	public void visit(VarDeclSingle varDeclSingle) {
	    Obj programObj = Tab.find(programName);

	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(varDeclSingle.getVarName())) {
	            Code.put(Code.const_);
	            Code.put4(0);                /** početna vrednost */
	            Code.put(Code.putstatic);
	            Code.put2(Code.dataSize);    /** offset u statičkom segmentu */
	            Code.dataSize++;
	            break;
	        }
	    }
	}

	/**
	 * Globalna deklaracija promenljive kada ih ima više u nizu (između zareza).
	 */
	public void visit(VarDeclBetween varDeclSingle) {
	    Obj programObj = Tab.find(programName);

	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(varDeclSingle.getVarName())) {
	            Code.put(Code.const_);
	            Code.put4(0);
	            Code.put(Code.putstatic);
	            Code.put2(Code.dataSize);
	            Code.dataSize++;
	            break;
	        }
	    }
	}

	/**
	 * Deklaracija niza — alocira prostor za referencu i pamti tip elemenata.
	 */
	public void visit(VarDeclArraySingle varDeclArray) {
	    Obj programObj = Tab.find(programName);

	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(varDeclArray.getVarName())) {
	            Code.put(Code.const_);
	            Code.put4(0);
	            Code.put(Code.putstatic);
	            Code.put2(Code.dataSize);
	            Code.dataSize++;
	            break;
	        }
	    }

	    /** pronađi tip niza iz okružujuće deklaracije */
	    SyntaxNode par = varDeclArray.getParent();
	    while (!(par instanceof VarDeclArrayList)) {
	        par = par.getParent();
	    }

	    currentListDecl = ((VarDeclArrayList) par).getType().struct;
	    nizelementi.put(varDeclArray.getVarName(), currentListDecl);
	}

	/**
	 * Deklaracija više nizova u istom redu.
	 */
	public void visit(VarDeclArrayBetween varDeclArray) {
	    Obj programObj = Tab.find(programName);

	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(varDeclArray.getVarName())) {
	            Code.put(Code.const_);
	            Code.put4(0);
	            Code.put(Code.putstatic);
	            Code.put2(Code.dataSize);
	            Code.dataSize++;
	            break;
	        }
	    }

	    /** koristi tip poslednjeg niza iz prethodne deklaracije */
	    nizelementi.put(varDeclArray.getVarName(), currentListDecl);
	}

	/**
	 * Deklaracija seta — rezerviše memoriju i postavlja početnu vrednost 0.
	 */
	public void visit(SetDeclSingle varDeclSet) {
	    Obj programObj = Tab.find(programName);

	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(varDeclSet.getVarName())) {
	            Code.put(Code.const_);
	            Code.put4(0);
	            Code.put(Code.putstatic);
	            Code.put2(Code.dataSize);
	            Code.dataSize++;
	            break;
	        }
	    }
	}

	/**
	 * Deklaracija više setova u istom redu.
	 */
	public void visit(SetDeclBetween varDeclSet) {
	    Obj programObj = Tab.find(programName);

	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(varDeclSet.getVarName())) {
	            Code.put(Code.const_);
	            Code.put4(0);
	            Code.put(Code.putstatic);
	            Code.put2(Code.dataSize);
	            Code.dataSize++;
	            break;
	        }
	    }
	}

	
	/** ---------------- KONSTANTE ---------------- */
	
	
	/**
	 * Deklaracija jedne konstante (npr. const x = ...).
	 * - U statičkom segmentu memorije rezerviše mesto za konstantu.
	 * - Postavlja početnu vrednost 0 (kasnije se zamenjuje realnom vrednošću).
	 * - Ako je u pitanju konstanta unutar klase, inicijalizuje i TVF polje.
	 */
	public void visit(ConstDeclarationSingle varDeclSingle) {
	    Obj programObj = Tab.find(programName);

	    /** zapamti adresu konstante u listi **/
	    constBrojevi.add(Code.dataSize);

	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(varDeclSingle.getConstName())) {
	            Code.put(Code.const_);
	            Code.put4(0);                  /** početna vrednost **/
	            Code.put(Code.putstatic);
	            Code.put2(Code.dataSize);      /** offset u statičkom segmentu **/
	            Code.dataSize++;

	            /** dodela vrednosti konstanti **/
	            Code.store(obj);

	            /** ako je deo klase, poveži TVF (tabelu virtuelnih funkcija) **/
	            if (newJeSad) {
	                Code.load(obj);
	                Code.loadConst(TVFKlase.get(NovaKlasa));
	                Code.put(Code.putfield);
	                Code.put2(0);              /** indeks polja u TVF tabeli **/
	                newJeSad = false;
	            }
	            break;
	        }
	    }
	}

	/**
	 * Deklaracija konstante kada ih ima više u nizu (između zareza).
	 * - Radi isto kao prethodna metoda, ali koristi već postojeći TVF kontekst.
	 */
	public void visit(ConstDeclarationBetween varDeclSingle) {
	    Obj programObj = Tab.find(programName);

	    /** zapamti adresu konstante **/
	    constBrojevi.add(Code.dataSize);

	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(varDeclSingle.getConstName())) {
	            Code.put(Code.const_);
	            Code.put4(0);
	            Code.put(Code.putstatic);
	            Code.put2(Code.dataSize);
	            Code.dataSize++;

	            /** dodela vrednosti konstanti **/
	            Code.store(obj);

	            /** ako je deo klase, poveži TVF **/
	            if (newJeSad) {
	                Code.load(obj);
	                Code.loadConst(TVFKlase.get(NovaKlasa));
	                Code.put(Code.putfield);
	                Code.put2(0);
	                newJeSad = false;
	            }
	            break;
	        }
	    }
	}
	
	/**
	 * =======================================================
	 * SEKCIJA: KLASE I INTERFEJSI
	 * =======================================================
	 */

	/**
	 * Zatvara obradu klase nakon završetka njenog tela.
	 * Resetuje referencu na trenutno aktivnu klasu.
	 */
	public void visit(ClassDeclaration classDecl) {
	    currentClass = null;
	}

	/**
	 * Deklaracija klase koja nasleđuje drugu klasu.
	 * Postavlja pokazivač na objekat koji predstavlja trenutnu klasu.
	 */
	public void visit(ClassNameExtends className) {
	    Obj programObj = Tab.find(programName);

	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(className.getClassName())) {
	            currentClass = obj;
	            break;
	        }
	    }
	}

	/**
	 * Deklaracija klase bez nasleđivanja.
	 * Postavlja pokazivač na trenutnu klasu i analizira njene članove.
	 * Broji polja i proverava da li postoji tabela virtuelnih funkcija (TVF).
	 */
	public void visit(ClassNameSingle className) {
	    Obj programObj = Tab.find(programName);

	    /** pronađi objekat klase po imenu **/
	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(className.getClassName())) {
	            currentClass = obj;
	            break;
	        }
	    }

	    /** struktura klase (sadrži polja i metode) **/
	    Struct struk = currentClass.getType();

	    /** prebrojava sve članove klase i traži polje "TVF" **/
	    int broj = 0;
	    for (Obj member : struk.getMembers()) {
	        if (member.getName().equals("TVF")) {
	            /** polje TVF — tabela virtuelnih funkcija **/
	        }
	        broj++;
	    }

	    /** broj polja i metoda u klasi (informativno) **/

	}

	/**
	 * Zatvara obradu interfejsa.
	 * Resetuje pokazivač na trenutni interfejs.
	 */
	public void visit(InterfaceDeclaration classDecl) {
	    currentClass = null;
	}

	/**
	 * Deklaracija interfejsa.
	 * Postavlja pokazivač na aktivni interfejs i analizira njegove članove.
	 * Proverava da li interfejs sadrži TVF polje i metode poput "display".
	 */
	public void visit(InterfaceNameSingle interName) {
	    Obj programObj = Tab.find(programName);

	    /** pronađi objekat interfejsa po imenu **/
	    for (Obj obj : programObj.getLocalSymbols()) {
	        if (obj.getName().equals(interName.getInterfaceName())) {
	            currentClass = obj;
	            break;
	        }
	    }

	}

	
	
	/**
	 * =======================================================
	 * SEKCIJA: METODE
	 * =======================================================
	 */

	/**
	 * Obrada završetka metode.
	 * Generiše instrukcije za izlazak iz funkcije i vraćanje kontrole pozivaocu.
	 */
	public void visit(MethodDecl methodDecl) {
	    daLiDoWhile(methodDecl);
	    methodType = null;
	    Code.put(Code.exit);
	    Code.put(Code.return_);
	    obradiDecu(methodDecl);
	}

	/**
	 * Deklaracija metode sa tipom povratne vrednosti.
	 * Ujedno generiše i inicijalizacionu funkciju programa (initMethod)
	 * i tabelu virtuelnih funkcija (TVF) za sve klase.
	 */
	public void visit(MethodTypeNameA methodTypeName) {
	    daLiDoWhile(methodTypeName);

	    // Kreiranje funkcije initMethod ako još nije generisana
	    if (!prvaFunc && currentClass == null) {
	        initFunc = Code.pc;
	        Obj initMethod = Tab.find("initMethod");
	        initMethod.setAdr(Code.pc);

	        // početak initMethod: generiši enter 0,0
	        Code.put(Code.enter);
	        Code.put(0);
	        Code.put(0);

	        // Inicijalizacija svih konstanti
	        for (int i = 0; i < constBrojevi.size(); i++) {
	            int a1 = constBrojevi.get(i);
	            int a2 = constVals.get(i);
	            Code.loadConst(a2);
	            Code.put(Code.putstatic);
	            Code.put2(a1);
	        }

	        // Alociranje pomoćnih statičkih promenljivih
	        pomStat1 = Code.dataSize;
	        Code.put(Code.const_);
	        Code.put4(0);
	        Code.put(Code.putstatic);
	        Code.put2(Code.dataSize);
	        Code.dataSize++;

	        pomStat2 = Code.dataSize;
	        Code.put(Code.const_);
	        Code.put4(0);
	        Code.put(Code.putstatic);
	        Code.put2(Code.dataSize);
	        Code.dataSize++;

	        pomStat3 = Code.dataSize;
	        Code.put(Code.const_);
	        Code.put4(0);
	        Code.put(Code.putstatic);
	        Code.put2(Code.dataSize);
	        Code.dataSize++;
	    }

	    methodType = methodTypeName.obj;
	    methodTypeName.obj.setAdr(Code.pc);

	    // Ako je ovo prva funkcija u programu (bez klase), generiši TVF
	    if (!prvaFunc && currentClass == null) {
	        prvaFunc = true;
	        Obj programObj = Tab.find(programName);
	        List<Obj> sveKlase = new ArrayList<>();

	        if (programObj != Tab.noObj) {
	            for (Obj obj : programObj.getLocalSymbols()) {
	                if ((obj.getType().getKind() == Struct.Class || obj.getType().getKind() == Struct.Interface)
	                        && obj.getKind() == Obj.Type) {
	                    sveKlase.add(obj);
	                }
	            }

	            // Generisanje TVF tabela za svaku klasu
	            for (Obj klasa : sveKlase) {
	                Struct structKlase = klasa.getType();
	                TVFKlase.put(klasa.getName(), Code.dataSize);

	                for (Obj clan : structKlase.getMembers()) {
	                    if (clan.getKind() != Obj.Meth) continue;

	                    String ime = clan.getName();
	                    adreseFunkcija.put(new AbstractMap.SimpleEntry<>(klasa.getName(), ime), clan.getAdr());

	                    // Nasleđivanje adrese ako metoda dolazi iz roditeljske klase
	                    if (clan.getAdr() == 0 && klasa.getType().getElemType() != null) {
	                        for (Obj member : klasa.getType().getElemType().getMembers()) {
	                            if (member.getKind() == Obj.Meth && member.getName().equals(clan.getName())) {
	                                clan.setAdr(member.getAdr());
	                                break;
	                            }
	                        }
	                    }

	                    // Upis imena metode karakter po karakter u statički segment
	                    for (int i = 0; i < ime.length(); i++) {
	                        char c = ime.charAt(i);
	                        Code.put(Code.const_);
	                        Code.put4((int) c);
	                        Code.put(Code.putstatic);
	                        Code.put2(Code.dataSize);
	                        Code.dataSize += 1;
	                    }

	                    // Znak -1 kao separator i upis adrese metode
	                    Code.put(Code.const_);
	                    Code.put4(-1);
	                    Code.put(Code.putstatic);
	                    Code.put2(Code.dataSize);
	                    Code.dataSize += 1;

	                    Code.put(Code.const_);
	                    Code.put4(clan.getAdr());
	                    Code.put(Code.putstatic);
	                    Code.put2(Code.dataSize);
	                    Code.dataSize += 1;
	                }

	                // Obeležava kraj tabele (-2)
	                Code.put(Code.const_);
	                Code.put4(-2);
	                Code.put(Code.putstatic);
	                Code.put2(Code.dataSize);
	                Code.dataSize += 1;
	            }
	        }

	        Code.put(Code.exit);
	        Code.put(Code.return_);
	        methodTypeName.obj.setAdr(Code.pc);
	    }

	    // Generisanje prologa metode (enter)
	    SyntaxNode methodNode = methodTypeName.getParent();
	    VarCounter varCnt = new VarCounter();
	    methodNode.traverseTopDown(varCnt);

	    FormParamCounter fpCnt = new FormParamCounter();
	    methodNode.traverseTopDown(fpCnt);

	    if ("main".equals(methodTypeName.getMethName())) mainPc = Code.pc;

	    if (currentClass != null) {
	        Code.put(Code.enter);
	        Code.put(fpCnt.getCount() + 1);
	        Code.put(fpCnt.getCount() + 1 + varCnt.getCount());
	    } else {
	        Code.put(Code.enter);
	        Code.put(fpCnt.getCount());
	        Code.put(fpCnt.getCount() + varCnt.getCount());
	    }

	    if ("main".equals(methodTypeName.getMethName())) {
	        Code.put(Code.call);
	        Code.put2(initFunc - Code.pc + 1);
	    }
	}

	/**
	 * Deklaracija void metode (bez povratne vrednosti).
	 * Radi isto što i MethodTypeNameA, ali bez povratnog tipa.
	 */
	public void visit(VoidMethodTypeName methodTypeName) {
	    daLiDoWhile(methodTypeName);

	    if (!prvaFunc && currentClass == null) {
	        initFunc = Code.pc;
	        Obj initMethod = Tab.find("initMethod");
	        initMethod.setAdr(Code.pc);

	        Code.put(Code.enter);
	        Code.put(0);
	        Code.put(0);

	        // Inicijalizacija konstanti
	        for (int i = 0; i < constBrojevi.size(); i++) {
	            int a1 = constBrojevi.get(i);
	            int a2 = constVals.get(i);
	            Code.loadConst(a2);
	            Code.put(Code.putstatic);
	            Code.put2(a1);
	        }

	        // Kreiranje pomoćnih statičkih promenljivih
	        pomStat1 = Code.dataSize;
	        Code.put(Code.const_);
	        Code.put4(0);
	        Code.put(Code.putstatic);
	        Code.put2(Code.dataSize);
	        Code.dataSize++;

	        pomStat2 = Code.dataSize;
	        Code.put(Code.const_);
	        Code.put4(0);
	        Code.put(Code.putstatic);
	        Code.put2(Code.dataSize);
	        Code.dataSize++;

	        pomStat3 = Code.dataSize;
	        Code.put(Code.const_);
	        Code.put4(0);
	        Code.put(Code.putstatic);
	        Code.put2(Code.dataSize);
	        Code.dataSize++;
	    }

	    methodType = methodTypeName.obj;
	    methodTypeName.obj.setAdr(Code.pc);

	    // Generisanje TVF tabela ako još nije urađeno
	    if (!prvaFunc && currentClass == null) {
	        prvaFunc = true;
	        Obj programObj = Tab.find(programName);
	        List<Obj> sveKlase = new ArrayList<>();

	        if (programObj != Tab.noObj) {
	            for (Obj obj : programObj.getLocalSymbols()) {
	                if (obj.getType().getKind() == Struct.Class && obj.getKind() == Obj.Type) {
	                    sveKlase.add(obj);
	                }
	            }

	            for (Obj klasa : sveKlase) {
	                Struct structKlase = klasa.getType();
	                TVFKlase.put(klasa.getName(), Code.dataSize);

	                for (Obj clan : structKlase.getMembers()) {
	                    if (clan.getKind() != Obj.Meth) continue;

	                    String ime = clan.getName();
	                    adreseFunkcija.put(new AbstractMap.SimpleEntry<>(klasa.getName(), ime), clan.getAdr());

	                    if (clan.getAdr() == 0 && klasa.getType().getElemType() != null) {
	                        for (Obj member : klasa.getType().getElemType().getMembers()) {
	                            if (member.getKind() == Obj.Meth && member.getName().equals(clan.getName())) {
	                                clan.setAdr(member.getAdr());
	                                break;
	                            }
	                        }
	                    }

	                    for (int i = 0; i < ime.length(); i++) {
	                        char c = ime.charAt(i);
	                        Code.put(Code.const_);
	                        Code.put4((int) c);
	                        Code.put(Code.putstatic);
	                        Code.put2(Code.dataSize);
	                        Code.dataSize += 1;
	                    }

	                    Code.put(Code.const_);
	                    Code.put4(-1);
	                    Code.put(Code.putstatic);
	                    Code.put2(Code.dataSize);
	                    Code.dataSize += 1;

	                    Code.put(Code.const_);
	                    Code.put4(clan.getAdr());
	                    Code.put(Code.putstatic);
	                    Code.put2(Code.dataSize);
	                    Code.dataSize += 1;
	                }

	                Code.put(Code.const_);
	                Code.put4(-2);
	                Code.put(Code.putstatic);
	                Code.put2(Code.dataSize);
	                Code.dataSize += 1;
	            }
	        }

	        Code.put(Code.exit);
	        Code.put(Code.return_);
	        methodTypeName.obj.setAdr(Code.pc);
	    }

	    // Generisanje prologa
	    SyntaxNode methodNode = methodTypeName.getParent();
	    VarCounter varCnt = new VarCounter();
	    methodNode.traverseTopDown(varCnt);

	    FormParamCounter fpCnt = new FormParamCounter();
	    methodNode.traverseTopDown(fpCnt);

	    if ("main".equals(methodTypeName.getMethName())) mainPc = Code.pc;

	    if (currentClass != null) {
	        Code.put(Code.enter);
	        Code.put(fpCnt.getCount() + 1);
	        Code.put(fpCnt.getCount() + 1 + varCnt.getCount());
	    } else {
	        Code.put(Code.enter);
	        Code.put(fpCnt.getCount());
	        Code.put(fpCnt.getCount() + varCnt.getCount());
	    }

	    if ("main".equals(methodTypeName.getMethName())) {
	        Code.put(Code.call);
	        Code.put2(initFunc - Code.pc + 1);
	    }
	}

	
	/**
	 * =======================================================
	 * SEKCIJA: NAREDBE (STATEMENTS)
	 * =======================================================
	 */

	/**
	 * Obrada potpuno uparenih (matched) naredbi.
	 * 
	 * MatchedStmt predstavlja sve naredbe koje su kompletne —
	 * na primer:
	 *   if (cond) stmt; else stmt;
	 *   do { ... } while (cond);
	 *   print(expr); return; break; continue; itd.
	 */
	public void visit(MatchedStmt matchedStmt) {
	    daLiDoWhile(matchedStmt);

	    SyntaxNode parent = matchedStmt.getParent();

	    // Ako se MatchedStmt nalazi unutar UnmatchedIf (viseći if),
	    // potrebno je generisati privremeni skok do ELSE grane
	    if (parent instanceof UnmatchedIf && ((UnmatchedIf) parent).getStatement() == matchedStmt) {
	        Code.putJump(0);
	        jumpMap.put(matchedStmt, Code.pc - 2);
	        obradi.computeIfAbsent(parent, k -> new ArrayList<>()).add(matchedStmt);
	    }

	    // Obradi sve potčvorove u AST-u koji pripadaju ovoj naredbi
	    obradiDecu(matchedStmt);
	}

	/**
	 * Obrada "visećih" (unmatched) naredbi.
	 *
	 * UnmatchedStmt predstavlja naredbe tipa:
	 *   if (cond) stmt;
	 *   if (cond) matched else unmatched;
	 * 
	 * tj. slučajeve gde parser još nije upario ELSE sa pripadajućim IF.
	 */
	public void visit(UnmatchedStmt unmatchedStmt) {
	    daLiDoWhile(unmatchedStmt);

	    SyntaxNode parent = unmatchedStmt.getParent();

	    // Ako je UnmatchedStmt dete UnmatchedIf čvora (viseći IF),
	    // generiši privremeni skok do ELSE dela
	    if (parent instanceof UnmatchedIf && ((UnmatchedIf) parent).getStatement() == unmatchedStmt) {
	        Code.putJump(0);
	        jumpMap.put(unmatchedStmt, Code.pc - 2);
	        obradi.computeIfAbsent(parent, k -> new ArrayList<>()).add(unmatchedStmt);
	    }

	    // Preuzmi sve čvorove koji čekaju da se fiksiraju skokovi
	    List<SyntaxNode> zaObradu = obradi.get(unmatchedStmt);

	    // Fiksiraj adrese iz mape "falseJumpMap"
	    if (zaObradu != null) {
	        for (SyntaxNode noda : zaObradu) {
	            Integer addr = falseJumpMap.get(noda);
	            if (addr != null) Code.fixup(addr);
	        }
	        obradi.remove(unmatchedStmt);
	    }

	    // Fiksiraj adrese iz mape "jumpMap"
	    if (zaObradu != null) {
	        for (SyntaxNode noda : zaObradu) {
	            Integer addr = jumpMap.get(noda);
	            if (addr != null) Code.fixup(addr);
	        }
	        obradi.remove(unmatchedStmt);
	    }
	}

	
	/** ---------------- UNMATCHED ---------------- **/
	
	
	public void visit(UnmatchedIf stmt) {
		daLiDoWhile(stmt);
		
		obradiDecu(stmt);
	}
	
	public void visit(UnmatchedIfElse umie) {
		daLiDoWhile(umie);
		
		obradiDecu(umie);
	}
	
	/** ---------------- MATCHED ---------------- **/
	
	
	
	/**
	 * Obrada naredbe `print(expr);`
	 * 
	 * Na osnovu tipa izraza (int, char, bool ili element niza),
	 * generiše odgovarajuću PRINT ili BPRINT instrukciju.
	 */
	public void visit(PrintStmtExprOnly printStmt) {
	    daLiDoWhile(printStmt);

	    Struct exprType = printStmt.getExpr().struct;
	    SyntaxNode parent = printStmt.getParent();

	    // Standardni tipovi — direktno ispisivanje
	    if (exprType == Tab.intType) {
	        Code.loadConst(5);
	        Code.put(Code.print);
	    } else if (exprType == Tab.charType) {
	        Code.loadConst(1);
	        Code.put(Code.bprint);
	    } else if (exprType == Tab.find("bool").getType()) {
	        Code.loadConst(5);
	        Code.put(Code.print);
	    } 
	    else {
	        // Poseban slučaj: ako je izraz element niza (niz[x])
	        Expr expr = printStmt.getExpr();

	        if (expr instanceof TermExpr) {
	            Term term = ((TermExpr) expr).getTerm();

	            if (term instanceof SingleFactor) {
	                Factor factor = ((SingleFactor) term).getFactor();

	                if (factor instanceof Var) {
	                    Designator designator = ((Var) factor).getDesignator();

	                    // Ako je pristup elementu niza
	                    if (designator instanceof ArrayDesignator) {
	                        Struct elemType = designator.obj.getType().getElemType();

	                        if (elemType.getKind() == Struct.Int) {
	                            Code.loadConst(5);
	                            Code.put(Code.print);
	                        } else if (elemType.getKind() == Struct.Char) {
	                            Code.loadConst(1);
	                            Code.put(Code.bprint);
	                        } else if (elemType.getKind() == Struct.Bool) {
	                            Code.loadConst(5);
	                            Code.put(Code.print);
	                        }
	                        return;
	                    } 
	                    // Ako se ispisuje ceo niz — prolazak kroz sve elemente
	                    else {
	                        Struct t = designator.obj.getType();

	                        if (t.getKind() == Struct.Array &&
	                            t.getElemType() == Tab.intType &&
	                            designator.obj.getFpPos() >= 0) {

	                            // Pomoćne promenljive (statics)
	                            Code.loadConst(1);
	                            Code.put(Code.putstatic);
	                            Code.put2(pomStat2);

	                            Code.load(designator.obj);
	                            Code.loadConst(0);
	                            Code.put(Code.aload);
	                            Code.put(Code.putstatic);
	                            Code.put2(pomStat3);

	                            int pc1 = Code.pc;
	                            Code.put(Code.getstatic);
	                            Code.put2(pomStat2);
	                            Code.put(Code.getstatic);
	                            Code.put2(pomStat3);

	                            // Petlja: i < dužina
	                            Code.putFalseJump(Code.le, 0);
	                            int pc = Code.pc - 2;

	                            // Ispis elementa niza
	                            Code.load(designator.obj);
	                            Code.put(Code.getstatic);
	                            Code.put2(pomStat2);
	                            Code.put(Code.aload);
	                            Code.loadConst(5);
	                            Code.put(Code.print);

	                            // i++
	                            Code.put(Code.getstatic);
	                            Code.put2(pomStat2);
	                            Code.loadConst(1);
	                            Code.put(Code.add);
	                            Code.put(Code.putstatic);
	                            Code.put2(pomStat2);

	                            // Skok na početak petlje
	                            Code.putJump(pc1);
	                            Code.fixup(pc);
	                        }
	                    }
	                }
	            }
	        }
	    }

	    kojiIfIliMatch(printStmt);
	    obradiDecu(printStmt);
	}

	/**
	 * Obrada naredbe `return expr;`
	 * 
	 * Završava trenutnu metodu i vraća se pozivaocu.
	 * Prethodno se očekuje da je rezultat izraza već na steku.
	 */
	public void visit(ReturnExpr returnExpr) {
	    daLiDoWhile(returnExpr);

	    Code.put(Code.exit);
	    Code.put(Code.return_);

	    kojiIfIliMatch(returnExpr);
	    obradiDecu(returnExpr);
	}

	/**
	 * Obrada naredbe `return;` (bez izraza)
	 * 
	 * Koristi se u funkcijama koje ne vraćaju rezultat (void).
	 */
	public void visit(ReturnNoExpr returnNoExpr) {
	    daLiDoWhile(returnNoExpr);

	    Code.put(Code.exit);
	    Code.put(Code.return_);

	    kojiIfIliMatch(returnNoExpr);
	    obradiDecu(returnNoExpr);
	}
	
	/**
	 * Obrada uparene IF-ELSE naredbe.
	 * 
	 * MatchedStatement predstavlja situacije gde su oba dela IF-a
	 * već zatvorena (if ... else ...), pa je potrebno samo obraditi
	 * eventualne skokove između grana.
	 */
	public void visit(MatchedStatement ms) {
	    daLiDoWhile(ms);

	    SyntaxNode parent = ms.getParent();

	    // Ako je MatchedStatement unutar drugog MatchedStatement-a
	    if (parent instanceof MatchedStatement && ((MatchedStatement) parent).getMatched() == ms) {
	        Code.putJump(0);
	        jumpMap.put(ms, Code.pc - 2);
	        // Roditeljski čvor će kasnije obraditi ovaj skok
	        obradi.computeIfAbsent(parent, k -> new ArrayList<>()).add(ms);
	    }

	    // Ako se MatchedStatement nalazi u UnmatchedIfElse kontekstu
	    if (parent instanceof UnmatchedIfElse && ((UnmatchedIfElse) parent).getMatched() == ms) {
	        Code.putJump(0);
	        jumpMap.put(ms, Code.pc - 2);
	        obradi.computeIfAbsent(parent, k -> new ArrayList<>()).add(ms);
	    }

	    // Rekurzivno obradi potčvorove (ako ih ima)
	    obradiDecu(ms);
	}

	/**
	 * Obrada naredbe `break;`
	 * 
	 * Generiše skok na kraj najbliže do-while petlje u kojoj se break nalazi.
	 * Skok se kasnije "popravlja" (fixup) kada se zna kraj petlje.
	 */
	public void visit(BreakStmt breakStmt) {
	    daLiDoWhile(breakStmt);

	    // Postavi instrukciju skoka (adresu ćemo popraviti kasnije)
	    Code.putJump(0);
	    jumpMap.put(breakStmt, Code.pc - 2);

	    // Pronađi prvi okružujući DO-WHILE blok
	    SyntaxNode parent = breakStmt.getParent();
	    while (!(parent instanceof DoWhileStmt
	          || parent instanceof DoWhileStmtExtra
	          || parent instanceof DoWhileEmpty)) {
	        parent = parent.getParent();
	    }

	    // Break se vezuje za kraj te petlje
	    obradi.computeIfAbsent(parent, k -> new ArrayList<>()).add(breakStmt);

	    obradiDecu(breakStmt);
	}

	/**
	 * Obrada naredbe `continue;`
	 * 
	 * Generiše skok nazad na početak DO-WHILE petlje.
	 * Ovaj skok se koristi da bi se preskočio ostatak tela
	 * i nastavila sledeća iteracija.
	 */
	public void visit(ContinueStmt continueStmt) {
	    daLiDoWhile(continueStmt);

	    // Generiši privremeni skok
	    Code.putJump(0);
	    jumpMap.put(continueStmt, Code.pc - 2);

	    // Pronađi odgovarajuću DO-WHILE petlju
	    SyntaxNode parent = continueStmt.getParent();
	    while (!(parent instanceof DoWhileStmt
	          || parent instanceof DoWhileStmtExtra
	          || parent instanceof DoWhileEmpty)) {
	        parent = parent.getParent();
	    }

	    // Odredi telo (blok) u koji se vraća CONTINUE
	    SyntaxNode block;
	    if (parent instanceof DoWhileStmt) {
	        block = ((DoWhileStmt) parent).getMatched();
	    } else if (parent instanceof DoWhileEmpty) {
	        block = ((DoWhileEmpty) parent).getMatched();
	    } else {
	        block = ((DoWhileStmtExtra) parent).getMatched();
	    }

	    // Veži continue za početak odgovarajućeg bloka
	    obradi.computeIfAbsent(block, k -> new ArrayList<>()).add(continueStmt);

	    obradiDecu(continueStmt);
	}
	

	/**
	 * 
	 * Poziva pomoćne metode za generisanje koda u zavisnosti
	 * od tipa naredbe i položaja u okviru IF/ELSE bloka.
	 */
	public void visit(DesignatorStmt designatorStmt) {
	    daLiDoWhile(designatorStmt);
	    kojiIfIliMatch(designatorStmt);
	    obradiDecu(designatorStmt);
	}

	/**
	 * Obrada naredbe `read(x);`
	 * 
	 * Učitava vrednost sa ulaza i smešta je u promenljivu
	 * ili element niza, u zavisnosti od tipa designatora.
	 */
	public void visit(ReadStmt node) {
	    daLiDoWhile(node);

	    Struct t = node.getDesignator().obj.getType();

	    // Odabir instrukcije prema tipu podatka
	    if (t == Tab.charType) {
	        Code.put(Code.bread);   // čitanje bajta (char)
	    } else {
	        Code.put(Code.read);    // čitanje celog broja ili bool vrednosti
	    }

	    // Smeštanje pročitanog podatka
	    if (node.getDesignator() instanceof ArrayDesignator) {
	        // niz[x] = ...  → koristi `astore`
	        Code.put(Code.astore);
	    } else {
	        // obična promenljiva: x = ...
	        Code.store(node.getDesignator().obj);
	    }

	    obradiDecu(node);
	}

	/**
	 * Obrada bloka naredbi `{ ... }`
	 * 
	 * Svaki blok ima sopstveni opseg i može sadržati
	 * proizvoljan broj naredbi i deklaracija.
	 */
	public void visit(BlockStmt blockStmt) {
	    daLiDoWhile(blockStmt);
	    kojiIfIliMatch(blockStmt);
	    obradiDecu(blockStmt);
	}

	/**
	 * Obrada naredbe `do { ... } while (uslov);`
	 * 
	 * Generiše povratni skok na početak DO-while petlje.
	 */
	public void visit(DoWhileStmt doWhileStmt) {
	    daLiDoWhile(doWhileStmt);

	    int adresa = dowhiles.get(doWhileStmt);
	    Code.putJump(adresa);

	    obradiDecu(doWhileStmt);
	}

	/**
	 * Obrada proširene naredbe `do { ... } while (uslov, extraStmt);`
	 * 
	 * Varijanta DO-while koja sadrži dodatnu naredbu
	 * u okviru uslova (npr. ažuriranje stanja, brojača, sl.).
	 */
	public void visit(DoWhileStmtExtra doWhileStmtExtra) {
	    daLiDoWhile(doWhileStmtExtra);

	    int adresa = dowhiles.get(doWhileStmtExtra);
	    Code.putJump(adresa);

	    obradiDecu(doWhileStmtExtra);
	}

	/**
	 * Obrada naredbe `do { ... } while ();`
	 * 
	 * DO-while petlja bez uslova (beskonačna petlja).
	 * Generiše beskonačan povratni skok na početak bloka.
	 */
	public void visit(DoWhileEmpty doWhileEmpty) {
	    daLiDoWhile(doWhileEmpty);

	    int adresa = dowhiles.get(doWhileEmpty);
	    Code.putJump(adresa);

	    obradiDecu(doWhileEmpty);
	}

	
	/** =========================================================
	 *                     IZRAZI I FAKTORI
	 * ========================================================= **/

	/** Sabiranje i oduzimanje (+, -) */
	public void visit(AddExpr addExpr) {
		daLiDoWhile(addExpr);
		
		if (addExpr.getAddop().getClass() == AddopPlus.class) {
			Code.put(Code.add);
		} else {
			Code.put(Code.sub);
		}
	}

	/** Negacija izraza (-expr) */
	public void visit(NegExpr negExpr) {
		daLiDoWhile(negExpr);
		Code.put(Code.neg);
	}

	/** Mapiranje niza funkcijom (map(f, arr)) */
	public void visit(MapExpr mapExpr) {
		Designator fNode = mapExpr.getDesignator();
		Designator arrNode = mapExpr.getDesignator1();
		Setop setop = mapExpr.getSetop();

		if (setop instanceof SetMap) {
			Code.load(arrNode.obj);      // niz
			Code.put(Code.arraylength);  // dužina niza

			Code.put(Code.putstatic);
			Code.put2(pomStat1);

			Code.put(Code.const_);
			Code.put4(0);                // i = 0
			Code.put(Code.putstatic);
			Code.put2(pomStat2);
			
			Code.loadConst(0);           // zbir = 0
			Code.put(Code.putstatic);
			Code.put2(pomStat3);
			
			// početak petlje
			int pc1 = Code.pc;
			Code.put(Code.getstatic);
			Code.put2(pomStat2);
			Code.put(Code.getstatic);
			Code.put2(pomStat1);
			
			Code.putFalseJump(Code.lt, 0);
			int pc = Code.pc - 2;

			// arr[i]
			Code.load(arrNode.obj);
			Code.put(Code.getstatic);
			Code.put2(pomStat2);
			Code.put(Code.aload); 
			
			// poziv funkcije f
			int offset = fNode.obj.getAdr() - Code.pc;
			Code.put(Code.call);
			Code.put2(offset);

			// zbir += rezultat
			Code.put(Code.getstatic);
			Code.put2(pomStat3);
			Code.put(Code.add);
			Code.put(Code.putstatic);
			Code.put2(pomStat3);
			
			// i++
			Code.put(Code.getstatic);
			Code.put2(pomStat2);
			Code.loadConst(1);
			Code.put(Code.add);
			Code.put(Code.putstatic);
			Code.put2(pomStat2);

			Code.putJump(pc1);
			Code.fixup(pc);

			// rezultat
			Code.put(Code.getstatic);
			Code.put2(pomStat3);
			
		}

	}

	/** Množenje, deljenje i mod (*, /, %) */
	public void visit(MulopTerm mulopTerm) {
		daLiDoWhile(mulopTerm);
		
		if (mulopTerm.getMulop().getClass() == MulopTimes.class) {
			Code.put(Code.mul);
		} else if (mulopTerm.getMulop().getClass() == MulopDiv.class) {
			Code.put(Code.div);
		} else {
			Code.put(Code.rem);
		}
	}

	
	/** ---------------- CONST ---------------- **/

	/** Celoobrojna konstanta (npr. const x = 5;) */
	public void visit(ConstNum cnstNum) {
		daLiDoWhile(cnstNum);
		
		// ako se konstanta nalazi u deklaraciji — zapamti vrednost
		SyntaxNode par = cnstNum.getParent();
		while (!(par instanceof Program)) {
			if (par instanceof ConstDeclList || par instanceof ConstDeclarationSingle || par instanceof ConstDeclarationBetween) {
				constVals.add(cnstNum.getN1());
				break;
			}
			par = par.getParent();
		}

		// upis u tabelu simbola
		Obj con = Tab.insert(Obj.Con, "$", cnstNum.struct);
		con.setLevel(0);
		con.setAdr(cnstNum.getN1());
		
		Code.load(con);
	}

	/** Karakterna konstanta (npr. const c = 'A';) */
	public void visit(ConstChar cnstChar) {
		daLiDoWhile(cnstChar);
		
		SyntaxNode par = cnstChar.getParent();
		while (!(par instanceof Program)) {
			if (par instanceof ConstDeclList || par instanceof ConstDeclarationSingle || par instanceof ConstDeclarationBetween) {
				constVals.add((int) cnstChar.getC1());
				break;
			}
			par = par.getParent();
		}

		Obj con = Tab.insert(Obj.Con, "$", cnstChar.struct);
		con.setLevel(0);
		con.setAdr(cnstChar.getC1()); // karakter kao int
		Code.load(con);
	}

	/** Logička konstanta (true / false) */
	public void visit(ConstBool cnstBool) {
		daLiDoWhile(cnstBool);
		
		SyntaxNode par = cnstBool.getParent();
		while (!(par instanceof Program)) {
			if (par instanceof ConstDeclList || par instanceof ConstDeclarationSingle || par instanceof ConstDeclarationBetween) {
				constVals.add(cnstBool.getB1() ? 1 : 0);
				break;
			}
			par = par.getParent();
		}

		Obj con = Tab.insert(Obj.Con, "$", cnstBool.struct);
		con.setLevel(0);
		con.setAdr(cnstBool.getB1() ? 1 : 0); // true = 1, false = 0
		Code.load(con);
	}

	
	
	/** ---------------- FUNCCALL ---------------- **/

	/** Poziv funkcije ili metode */
	public void visit(FuncCall funcCall) {
		daLiDoWhile(funcCall);
		String fname = funcCall.getDesignator().obj.getName();

		// ord / chr — bez generisanja poziva
		if (fname.equals("ord") || fname.equals("chr")) {
			obradiDecu(funcCall);
			return;
		}

		Obj functionObj = funcCall.getDesignator().obj;
		int offset = functionObj.getAdr() - Code.pc;

		// Ako je designator direktno ime (bez polja)
		if (funcCall.getDesignator() instanceof DesignatorIdent) {
			String name = ((DesignatorIdent) funcCall.getDesignator()).getName();

			if (name.equals("len")) {
				// len(niz): niz već na steku
				Code.put(Code.arraylength);
				obradiDecu(funcCall);
				return;
			}

			// Metoda klase → invokevirtual
			if (functionObj.getKind() == Obj.Meth && functionObj.getLevel() == 1) {
				Code.put(Code.load_n + 0);  // this
				Code.put(Code.getfield);
				Code.put2(0);               // TVF pointer

				Code.put(Code.invokevirtual);
				String methodName = functionObj.getName();
				for (int i = 0; i < methodName.length(); i++)
					Code.put4(methodName.charAt(i));
				Code.put4(-1); // terminator
			} else {
				// Običan globalni poziv
				Code.put(Code.call);
				Code.put2(offset);
			}
		}

		// Poziv metode kroz objekat (npr. obj.metod())
		else if (funcCall.getDesignator() instanceof FieldAccess) {
			FieldAccess fa = (FieldAccess) funcCall.getDesignator();
			fa.getDesignator().traverseBottomUp(this);

			Code.put(Code.getfield);
			Code.put2(0); // TVF pointer

			Code.put(Code.invokevirtual);
			String methodName = fa.getFieldName();
			for (int i = 0; i < methodName.length(); i++)
				Code.put4(methodName.charAt(i));
			Code.put4(-1); // terminator
		}

		obradiDecu(funcCall);
	}

	
	/** ---------------- NEW ALLOCATION ---------------- **/

	/** Alokacija novog niza (new Type[size]) */
	public void visit(NewArray newArray) {
		daLiDoWhile(newArray);

		Code.put(Code.newarray);
		if (newArray.getType().struct == Tab.charType) {
			Code.put(0); // karakteri
		} else {
			Code.put(1); // ostali tipovi
		}
	}

	/** Alokacija novog seta (new set {expr}) */
	public void visit(NewSet newSet) {
		daLiDoWhile(newSet);

		Code.loadConst(1);
		Code.put(Code.add);
		Code.put(Code.newarray);
		Code.put(1);

		Code.put(Code.dup);     // duplira adresu niza (treba dva puta)
		Code.loadConst(0);      // indeks = 0
		Code.loadConst(0);      // vrednost = 0
		Code.put(Code.astore);  // niz[0] = 0 (int[])
	}

	/** Alokacija nove klase (new Class()) */
	public void visit(NewClass newClass) {
		daLiDoWhile(newClass);

		Code.put(Code.new_);

		// broj atributa klase (Fld članovi)
		int numFields = 0;
		for (Obj o : newClass.getType().struct.getMembers()) {
			if (o.getKind() == Obj.Fld)
				numFields++;
		}

		Code.put2(numFields * 4); // 4 bajta po polju
		newJeSad = true;
		NovaKlasa = newClass.getType().getTypeName();

		obradiDecu(newClass);
	}

	
	/** =========================================================
	 *                     POZIVI I DESIGNATORI
	 * ========================================================= **/

	/** Identifikator (varijabla, polje klase, funkcija) */
	public void visit(DesignatorIdent designator) {
		daLiDoWhile(designator);

		// Ako je polje unutar klase, učitaj this
		if (currentClass != null) {
			Struct classType = currentClass.getType();
			for (Obj o : classType.getMembers()) {
				if (o.getName().equals(designator.getName())) {
					Code.put(Code.load_n + 0);
				}
			}
		}

		SyntaxNode parent = designator.getParent();

		// Automatski učitavanje vrednosti ako nije deo dodele ili poziva
		if (Assignment.class != parent.getClass()
				&& FuncCall.class != parent.getClass()
				&& IncStmt.class != parent.getClass()
				&& DecStmt.class != parent.getClass()
				&& FuncCallStmt.class != parent.getClass()
				&& MapExpr.class != parent.getClass()
				&& ReadStmt.class != parent.getClass()) {
			Code.load(designator.obj);
		}

		obradiDecu(designator);
	}

	/** Element niza (niz[i]) */
	public void visit(ArrayDesignator designator) {
		daLiDoWhile(designator);
		SyntaxNode parent = designator.getParent();

		if ((parent instanceof IncStmt) || (parent instanceof DecStmt)) {
			Code.put(Code.pop);
			if (designator.obj.getKind() == Obj.Fld) {
				Code.put(Code.load_n + 0);
			}
			Code.load(designator.obj);
			designator.getExpr().traverseBottomUp(this);
			if (designator.obj.getKind() == Obj.Fld) {
				Code.put(Code.load_n + 0);
			}
			Code.load(designator.obj);
			designator.getExpr().traverseBottomUp(this);
			Code.put(Code.aload);
		}

		else if (!(parent instanceof Assignment)
				&& !(parent instanceof FuncCall)
				&& !(parent instanceof FuncCallStmt)) {

			Code.put(Code.pop);
			if (designator.obj.getKind() == Obj.Fld) {
				Code.put(Code.load_n + 0);
			}
			Code.load(designator.obj);
			designator.getExpr().traverseBottomUp(this);

			if (!(parent instanceof ReadStmt)) {
				Code.put(Code.aload);
			}
		}

		else {
			Code.put(Code.pop);
			if (designator.obj.getKind() == Obj.Fld) {
				Code.put(Code.load_n + 0);
			}
			Code.load(designator.obj);
			designator.getExpr().traverseBottomUp(this);
		}
	}

	/** Pristup polju objekta (obj.field) */
	public void visit(FieldAccess fieldAccess) {
		daLiDoWhile(fieldAccess);
		SyntaxNode parent = fieldAccess.getParent();

		if (Assignment.class != parent.getClass()
				&& FuncCall.class != parent.getClass()
				&& IncStmt.class != parent.getClass()
				&& DecStmt.class != parent.getClass()
				&& FuncCallStmt.class != parent.getClass()
				&& ReadStmt.class != parent.getClass()) {
			Code.load(fieldAccess.obj);
		} else if (IncStmt.class == parent.getClass() || DecStmt.class == parent.getClass()) {
			Code.load(fieldAccess.getDesignator().obj);
		}

		obradiDecu(fieldAccess);
	}

	/** Dodela vrednosti (a = b) */
	public void visit(Assignment assignment) {
		daLiDoWhile(assignment);

		Designator desi = assignment.getDesignator();
		Expr exp = assignment.getExpr();

		// Specijalan slučaj: s1 = s2 union s3
		if (exp instanceof MapExpr) {
			MapExpr mapi = (MapExpr) exp;
			Setop setic = mapi.getSetop();

			if (setic instanceof SetUnion) {
				Designator seti1 = mapi.getDesignator();
				Designator seti2 = mapi.getDesignator1();
				Designator seti3 = assignment.getDesignator();

				Code.load(seti1.obj);
				Code.loadConst(0);
				Code.put(Code.aload);
				Code.put(Code.putstatic);
				Code.put2(pomStat1);

				Code.put(Code.const_);
				Code.put4(1);
				Code.put(Code.putstatic);
				Code.put2(pomStat2);

				int pc1 = Code.pc;
				Code.put(Code.getstatic);
				Code.put2(pomStat2);
				Code.put(Code.getstatic);
				Code.put2(pomStat1);

				Code.putFalseJump(Code.le, 0);
				int pc = Code.pc - 2;

				Code.load(seti3.obj);
				Code.load(seti1.obj);
				Code.put(Code.getstatic);
				Code.put2(pomStat2);
				Code.put(Code.aload);

				Code.put(Code.call);
				int offset = -Code.pc + 1;
				Code.put2(offset);

				Code.put(Code.getstatic);
				Code.put2(pomStat2);
				Code.loadConst(1);
				Code.put(Code.add);
				Code.put(Code.putstatic);
				Code.put2(pomStat2);

				Code.putJump(pc1);
				Code.fixup(pc);

				Code.load(seti2.obj);
				Code.loadConst(0);
				Code.put(Code.aload);
				Code.put(Code.putstatic);
				Code.put2(pomStat1);

				Code.put(Code.const_);
				Code.put4(1);
				Code.put(Code.putstatic);
				Code.put2(pomStat2);

				pc1 = Code.pc;
				Code.put(Code.getstatic);
				Code.put2(pomStat2);
				Code.put(Code.getstatic);
				Code.put2(pomStat1);

				Code.putFalseJump(Code.le, 0);
				pc = Code.pc - 2;

				Code.load(seti3.obj);
				Code.load(seti2.obj);
				Code.put(Code.getstatic);
				Code.put2(pomStat2);
				Code.put(Code.aload);

				Code.put(Code.call);
				offset = -Code.pc + 1;
				Code.put2(offset);

				Code.put(Code.getstatic);
				Code.put2(pomStat2);
				Code.loadConst(1);
				Code.put(Code.add);
				Code.put(Code.putstatic);
				Code.put2(pomStat2);

				Code.putJump(pc1);
				Code.fixup(pc);
			}
		}

		else if (assignment.getDesignator() instanceof ArrayDesignator) {
			Code.put(Code.astore);

			if (newJeSad) {
				ArrayDesignator ad = (ArrayDesignator) assignment.getDesignator();

				Code.load(ad.obj);
				ad.getExpr().traverseBottomUp(this);
				Code.put(Code.aload);

				Code.loadConst(TVFKlase.get(NovaKlasa));
				Code.put(Code.putfield);
				Code.put2(0);

				newJeSad = false;
			}
		}

		else {
			Code.store(assignment.getDesignator().obj);

			if (newJeSad) {
				Code.load(assignment.getDesignator().obj);
				Code.loadConst(TVFKlase.get(NovaKlasa));
				Code.put(Code.putfield);
				Code.put2(0);
				newJeSad = false;
			}
		}

		obradiDecu(assignment);
	}

	/** Inkrement (x++) */
	public void visit(IncStmt incStmt) {
		daLiDoWhile(incStmt);

		if (incStmt.getDesignator().obj.getKind() == Obj.Fld) {
			if (!(incStmt.getDesignator() instanceof ArrayDesignator)) {
				Code.put(Code.load_n + 0);
			}
		}

		if (!(incStmt.getDesignator() instanceof ArrayDesignator)) {
			Code.load(incStmt.getDesignator().obj);
		}

		Code.loadConst(1);
		Code.put(Code.add);

		if (incStmt.getDesignator() instanceof ArrayDesignator) {
			Code.put(Code.astore);
		} else {
			Code.store(incStmt.getDesignator().obj);
		}
	}

	/** Dekrement (x--) */
	public void visit(DecStmt decStmt) {
		daLiDoWhile(decStmt);

		Code.load(decStmt.getDesignator().obj);
		Code.loadConst(1);
		Code.put(Code.sub);
		Code.store(decStmt.getDesignator().obj);
	}

	/** Poziv funkcije kao izraza (bez vraćanja vrednosti) */
	public void visit(FuncCallStmt funcCall) {
		daLiDoWhile(funcCall);
		String fname = funcCall.getDesignator().obj.getName();

		if (fname.equals("ord") || fname.equals("chr") || fname.equals("len")) {
			return;
		}

		Obj functionObj = funcCall.getDesignator().obj;
		int offset = functionObj.getAdr() - Code.pc;

		if (funcCall.getDesignator() instanceof DesignatorIdent) {
			if (functionObj.getKind() == Obj.Meth && functionObj.getLevel() == 1) {
				Code.put(Code.load_n + 0);
				Code.put(Code.getfield);
				Code.put2(0);

				Code.put(Code.invokevirtual);
				String methodName = functionObj.getName();
				for (int i = 0; i < methodName.length(); i++) {
					Code.put4(methodName.charAt(i));
				}
				Code.put4(-1);
			} else {
				Code.put(Code.call);
				Code.put2(offset);
			}
		} else if (funcCall.getDesignator() instanceof FieldAccess) {
			FieldAccess fa = (FieldAccess) funcCall.getDesignator();
			fa.getDesignator().traverseBottomUp(this);

			Code.put(Code.getfield);
			Code.put2(0);

			Code.put(Code.invokevirtual);
			String methodName = fa.getFieldName();
			for (int i = 0; i < methodName.length(); i++) {
				Code.put4(methodName.charAt(i));
			}
			Code.put4(-1);
		}

		obradiDecu(funcCall);
	}

	
	
	/** =========================================================
	 *                     USLOVI (CONDITIONS)
	 * ========================================================= */

	public void visit(SingleCondTerm sgt) {
		daLiDoWhile(sgt);
		obradiDecu(sgt);
	}

	public void visit(OrCondition orCondition) {
		daLiDoWhile(orCondition);
		obradiDecu(orCondition);
	}

	public void visit(SingleCondFact singleCondFact) {
		daLiDoWhile(singleCondFact);
		obradiDecu(singleCondFact);
	}

	public void visit(AndCondTerm andCondTerm) {
		daLiDoWhile(andCondTerm);
		obradiDecu(andCondTerm);
	}

	public void visit(RelationalCond condFact) {
		daLiDoWhile(condFact);

		Struct left = condFact.getExpr().struct;
		Struct right = condFact.getExpr1().struct;

		int op = -1;
		if (condFact.getRelop() instanceof Equal) op = Code.eq;
		else if (condFact.getRelop() instanceof NotEqual) op = Code.ne;
		else if (condFact.getRelop() instanceof Greater) op = Code.gt;
		else if (condFact.getRelop() instanceof GreaterEqual) op = Code.ge;
		else if (condFact.getRelop() instanceof Less) op = Code.lt;
		else if (condFact.getRelop() instanceof LessEqual) op = Code.le;

		SyntaxNode parent = condFact.getParent();
		if (parent == null) return; // sigurnosna provera

		SyntaxNode prev = parent;
		boolean shouldInvertJump = false;

		if (parent instanceof AndCondTerm) {
			while (parent instanceof AndCondTerm && parent.getParent() != null) {
				prev = parent;
				parent = parent.getParent();
			}
			if (parent instanceof OrCondition) {
				Code.putFalseJump(op, 0);
				falseJumpMap.put(condFact, Code.pc - 2);
				obradi.computeIfAbsent(prev, k -> new ArrayList<>()).add(condFact);
				obradiDecu(condFact);
				return;
			} else {
				parent = parent.getParent();
				while (parent instanceof OrCondition && parent.getParent() != null) {
					parent = parent.getParent();
				}
			}
		} else {
			parent = parent.getParent();
			prev = parent;
			while (parent instanceof AndCondTerm && parent.getParent() != null) {
				prev = parent;
				parent = parent.getParent();
			}
			if (parent instanceof SingleCondTerm) {
				parent = parent.getParent();
				while (parent instanceof OrCondition && parent.getParent() != null) {
					parent = parent.getParent();
				}
			} else {
				prev = parent;
				while (parent instanceof OrCondition && parent.getParent() != null) {
					prev = parent;
					parent = parent.getParent();
				}
				parent = prev;
				shouldInvertJump = true;
			}
		}

		if (shouldInvertJump)
			Code.putFalseJump(Code.inverse[op], 0);
		else
			Code.putFalseJump(op, 0);

		falseJumpMap.put(condFact, Code.pc - 2);

		if (parent instanceof MatchedStatement) {
			SyntaxNode matched = ((MatchedStatement) parent).getMatched();
			obradi.computeIfAbsent(matched, k -> new ArrayList<>()).add(condFact);
		} else if (parent instanceof UnmatchedIfElse) {
			SyntaxNode matched = ((UnmatchedIfElse) parent).getMatched();
			obradi.computeIfAbsent(matched, k -> new ArrayList<>()).add(condFact);
		} else if (parent instanceof UnmatchedIf) {
			SyntaxNode state = ((UnmatchedIf) parent).getStatement();
			obradi.computeIfAbsent(state, k -> new ArrayList<>()).add(condFact);
		} else if (parent instanceof DoWhileStmt
				|| parent instanceof DoWhileStmtExtra
				|| parent instanceof DoWhileEmpty
				|| parent instanceof OrCondition
				|| parent instanceof AndCondTerm) {
			obradi.computeIfAbsent(parent, k -> new ArrayList<>()).add(condFact);
		}

		obradiDecu(condFact);
	}

	public void visit(OnlyExprCond condFact) {
		daLiDoWhile(condFact);

		SyntaxNode parent = condFact.getParent();
		if (parent == null) return; // zaštita
		SyntaxNode prev = parent;
		boolean shouldInvertJump = false;

		if (parent instanceof AndCondTerm) {
			while (parent instanceof AndCondTerm && parent.getParent() != null) {
				prev = parent;
				parent = parent.getParent();
			}
			if (parent instanceof OrCondition) {
				Code.putFalseJump(Code.eq, 0);
				falseJumpMap.put(condFact, Code.pc - 2);
				obradi.computeIfAbsent(prev, k -> new ArrayList<>()).add(condFact);
				obradiDecu(condFact);
				return;
			} else {
				parent = parent.getParent();
				while (parent instanceof OrCondition && parent.getParent() != null) {
					parent = parent.getParent();
				}
			}
		} else {
			parent = parent.getParent();
			prev = parent;
			while (parent instanceof AndCondTerm && parent.getParent() != null) {
				prev = parent;
				parent = parent.getParent();
			}
			if (parent instanceof SingleCondTerm) {
				parent = parent.getParent();
				while (parent instanceof OrCondition && parent.getParent() != null) {
					parent = parent.getParent();
				}
			} else {
				prev = parent;
				while (parent instanceof OrCondition && parent.getParent() != null) {
					prev = parent;
					parent = parent.getParent();
				}
				parent = prev;
				shouldInvertJump = true;
			}
		}

		Code.loadConst(1);
		if (shouldInvertJump)
			Code.putFalseJump(Code.ne, 0);
		else
			Code.putFalseJump(Code.eq, 0);

		falseJumpMap.put(condFact, Code.pc - 2);

		if (parent instanceof MatchedStatement) {
			SyntaxNode matched = ((MatchedStatement) parent).getMatched();
			obradi.computeIfAbsent(matched, k -> new ArrayList<>()).add(condFact);
		} else if (parent instanceof UnmatchedIfElse) {
			SyntaxNode matched = ((UnmatchedIfElse) parent).getMatched();
			obradi.computeIfAbsent(matched, k -> new ArrayList<>()).add(condFact);
		} else if (parent instanceof UnmatchedIf) {
			SyntaxNode state = ((UnmatchedIf) parent).getStatement();
			obradi.computeIfAbsent(state, k -> new ArrayList<>()).add(condFact);
		} else if (parent instanceof DoWhileStmt
				|| parent instanceof DoWhileStmtExtra
				|| parent instanceof DoWhileEmpty
				|| parent instanceof OrCondition
				|| parent instanceof AndCondTerm) {
			obradi.computeIfAbsent(parent, k -> new ArrayList<>()).add(condFact);
		}

		obradiDecu(condFact);
	}

	
	
	/** =========================================================
	 *                     INTERFEJS METODE
	 * ========================================================= */

	/** Default metoda u interfejsu */
	public void visit(DefaultMethodDecl methodDecl) {
		daLiDoWhile(methodDecl);
		
		methodType = null;

		Code.put(Code.exit);
		Code.put(Code.return_);

		obradiDecu(methodDecl);
	}

	/** Apstraktna metoda u interfejsu */
	public void visit(AbstractMethodDecl methodDecl) {
		daLiDoWhile(methodDecl);
		
		methodType = null;

		SyntaxNode node = methodDecl.getMethodTypeName();

		// ako metoda ima tip (nije void) — vraća 0
		if (!(node instanceof VoidMethodTypeName)) {
			Code.loadConst(0);
		}

		Code.put(Code.exit);
		Code.put(Code.return_);

		obradiDecu(methodDecl);
	}

	
	
	
	/** =========================================================
	 *                     UGRADJENE METODE
	 * ========================================================= */


	
	//ovde su ugradjene funkcije za addAll i add
	//metoda add
	public void generateBuiltinFunctions() {
	    Code.put(Code.enter);
	    Code.put(2);
	    Code.put(5);
	    
	    Code.loadConst(1);
	    Code.put(Code.store);
	    Code.put(3);
	    
	    Code.put(Code.load_n + 0);
	    Code.put(Code.arraylength);
	    Code.put(Code.store);
	    Code.put(4);
	    
	    Code.put(Code.load_n + 0);
	    Code.loadConst(0);
	    Code.put(Code.aload);
	    Code.put(Code.store);
	    Code.put(2);
	    
	    Code.put(Code.load_n + 2);
	    Code.put(Code.load);
	    Code.put(4);
	    Code.put(Code.jcc + Code.le);
	    Code.put2(8);
	    Code.put(Code.exit);
	    Code.put(Code.return_);
	    Code.put(Code.jmp);
	    Code.put2(3);
	    Code.put(Code.load_n + 3);
	    Code.put(Code.pop);
	    Code.put(Code.load_n + 0);
	    Code.put(Code.load_n + 3);
	    Code.put(Code.aload);
	    Code.put(Code.load_n + 1);
	    Code.put(Code.jcc + Code.ne);
	    Code.put2(8);
	    Code.put(Code.exit);
	    Code.put(Code.return_);
	    Code.put(Code.jmp);
	    Code.put2(3);
	    Code.put(Code.load_n + 3);
	    Code.put(Code.const_1);
	    Code.put(Code.add);
	    Code.put(Code.store_n + 3);
	    Code.put(Code.load_n + 3);
	    Code.put(Code.load_n + 2);
	    Code.put(Code.jcc + Code.gt);
	    Code.put2(6);
	    Code.put(Code.jmp);
	    Code.put2(-23);
	    
	    Code.put(Code.load_n + 2);
	    Code.loadConst(0);
	    Code.put(Code.jcc + Code.ne);
	    Code.put2(10);
	    Code.put(Code.load_n + 3);
	    Code.put(Code.const_1);
	    Code.put(Code.sub);
	    Code.put(Code.store_n + 3);
	    Code.put(Code.jmp);
	    Code.put2(3);
	    
	    Code.put(Code.load_n + 3);
	    Code.put(Code.pop);
	    Code.put(Code.load_n + 0);
	    Code.put(Code.load_n + 3);
	    Code.put(Code.load_n + 1);
	    Code.put(Code.astore);
	    Code.put(Code.load_n + 2);
	    Code.put(Code.const_1);
	    Code.put(Code.add);
	    Code.put(Code.store_n + 2);
	    Code.loadConst(0);
	    Code.put(Code.pop);
	    Code.put(Code.load_n + 0);
	    Code.loadConst(0);
	    Code.put(Code.load_n + 0);
	    Code.loadConst(0);
	    Code.put(Code.aload);
	    Code.put(Code.const_1);
	    Code.put(Code.add);
	    Code.put(Code.astore);
	    Code.put(Code.exit);
	    Code.put(Code.return_);
	    Code.put(Code.exit);
	    Code.put(Code.return_);
	}

	
	//metoda addAll
	public void generateBuiltinFunctionsAddAll() {
	    Code.put(Code.enter);
	    Code.put(2);
	    Code.put(4);
	    
	    Code.loadConst(0);
	    Code.put(Code.store_n + 3);
	    
	    Code.put(Code.load_n + 1);
	    Code.put(Code.arraylength);
	    Code.put(Code.store_n + 2);
	    
	    Code.put(Code.load_n + 0);
	    Code.put(Code.load_n + 3);
	    Code.put(Code.pop);
	    Code.put(Code.load_n + 1);
	    Code.put(Code.load_n + 3);
	    Code.put(Code.aload);
	    Code.put(Code.call);
	    Code.put2(0 - Code.pc + 1);
	    Code.put(Code.load_n + 3);
	    Code.put(Code.const_1);
	    Code.put(Code.add);
	    Code.put(Code.store_n + 3);
	    Code.put(Code.load_n + 3);
	    Code.put(Code.load_n + 2);
	    Code.put(Code.jcc + Code.ge);
	    Code.put2(6);
	    Code.put(Code.jmp);
	    Code.put2(-18);
	    Code.put(Code.exit);
	    Code.put(Code.return_);
	    Code.put(Code.exit);
	    Code.put(Code.return_);
	}

	
}


// --- End of CodeGenerator.java ---
