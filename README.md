# 🎯 PP1 Projekat – Mikrojava Compiler

Ovaj repozitorijum sadrži moj projekat iz predmeta **Programski prevodioci 1 (PP1)** na Elektrotehničkom fakultetu u Beogradu.  
Cilj projekta je implementacija **prevodioca za jezik Mikrojava** sa podrškom za sve faze obrade programa: leksičku, sintaksnu, semantičku analizu i generisanje bajtkoda.

---

## 📁 Struktura projekta

```
ProjekatPP1/
 ├── spec/      → Leksička i sintaksna specifikacija (.lex, .cup)
 ├── src/       → Izvorni kod (Java fajlovi – analizatori, semantika, generator koda)
 ├── test/      → Test primeri (.mj fajlovi sa Mikrojava kodom)
 ├── README.md  → Opis projekta
 └── .gitignore → Ignorisani fajlovi (IDE, .class, out, build itd.)
```

- **spec/** – sadrži `mjlexer.lex` i `mjparser.cup` fajlove koji definišu tokene i gramatička pravila jezika.  
- **src/** – implementacija semantičke analize (`SemanticPass`), generisanja koda (`CodeGenerator`), i dodatnih klasa (`SyntaxTreePrinter`, novi tipovi čvorova, itd.).  
- **test/** – primeri Mikrojava programa korišćeni za testiranje svih funkcionalnosti po nivoima (A, B, C).

---

## ⚙️ Faze rada kompajlera

1. **Leksička analiza**  
   - Prepoznaje osnovne elemente jezika (tokene) pomoću **JFlex** alata.  
   - Definiše ključne reči, identifikatore, konstante i operatore.

2. **Sintaksna analiza**  
   - Kreira sintaksno stablo pomoću **CUP** parser generatora.  
   - Proverava da li program poštuje gramatička pravila jezika.

3. **Semantička analiza**  
   - Proverava tipove, deklaracije i pravila jezika.  
   - Otkriva greške koje nisu sintaksne (npr. pogrešni tipovi izraza).

4. **Generisanje koda**  
   - Pretvara sintaksno stablo u bajtkod koji se može izvršiti u **Mikrojava emulatoru**.

---

## 🧩 Nivoi projekta

### 🔹 **NIVO A – Osnovne konstrukcije i rad sa nizovima i skupovima**

Na ovom nivou implementirano je generisanje koda za osnovne programske konstrukcije.  
Podržani su aritmetički izrazi, pozivi predefinisanih metoda, rad sa nizovima i skupovima celih brojeva, kao i operacije unije nad skupovima.  
Program mora da sadrži funkciju `main`, kao i globalne i lokalne promenljive (proste i nizovne).

### 🔹 **NIVO B – Kontrolne strukture i funkcije**

Na drugom nivou dodate su sve konstrukcije iz nivoa A, uz implementaciju kontrolnih struktura (`if`, `else`, `do-while`, `break`, `continue`), kao i funkcija sa i bez povratne vrednosti.  
Podržani su uslovni izrazi, logičke operacije (`&&`, `||`), i mogućnost pozivanja globalnih funkcija i metoda između jedinica programa.

### 🔹 **NIVO C – Objektno-orijentisane ekstenzije**

Treći nivo proširuje Mikrojava jezik podrškom za **objektno-orijentisano programiranje**.  
Implementirano je nasleđivanje klasa, kreiranje objekata i nizova objekata, interfejsi sa podrazumevanim metodama, kao i tabele virtuelnih funkcija koje omogućavaju **polimorfno pozivanje metoda**.  
Na ovom nivou omogućena je i **supstitucija** – prosleđivanje objekata izvedenih klasa tamo gde se očekuju reference na osnovne klase ili interfejse.

---

## 🚀 Pokretanje projekta

1. Generisanje analizatora:
   ```bash
   java -jar tools/JFlex.jar spec/mjlexer.lex
   java -jar tools/java-cup-11b.jar -parser MJParser -symbols sym spec/mjparser.cup
   ```

2. Kompajliranje svih Java fajlova:
   ```bash
   javac -cp .;tools/java-cup-11b-runtime.jar src/**/*.java
   ```

3. Pokretanje test programa:
   ```bash
   java -cp .;tools/java-cup-11b-runtime.jar rs.etf.pp1.MJParser test/test1.mj
   ```

Ako je kod ispravan, generiše se `.obj` fajl koji se može pokrenuti pomoću **Mikrojava emulatora**.

---

## 📌 Napomene

- Projekat je razvijen **na osnovu zvaničnog ETF PP1 skeletona**, ali **u repozitorijumu nisu uključeni originalni fajlovi skeletona**.  
- U repozitorijumu su ostavljene **samo klase i proširenja koja sam samostalno implementirao**.  
- Glavne izmene i proširenja izvršene su u:
  - `SemanticPass.java`
  - `CodeGenerator.java`
  - dodatnim tipovima čvorova u AST stablu (ako su korišćeni).
- Test primeri su ručno pripremljeni tako da pokrivaju sve funkcionalnosti za nivoe A, B i C.

---

## 👨‍💻 Autor

**Nikola Vučićević**  
Smer: Softversko inženjerstvo  
Elektrotehnički fakultet, Univerzitet u Beogradu  
Godina: 2025.
