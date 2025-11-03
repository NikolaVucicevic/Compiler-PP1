
package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;

%%

%{

	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type) {
		return new Symbol(type, yyline+1, yycolumn);
	}
	
	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type, Object value) {
		return new Symbol(type, yyline+1, yycolumn, value);
	}

%}

%cup
%line
%column

%xstate COMMENT

%eofval{
	return new_symbol(sym.EOF);
%eofval}

%%

" " 	{ }
"\b" 	{ }
"\t" 	{ }
"\r\n" 	{ }
"\f" 	{ }

"program"   { return new_symbol(sym.PROG, yytext());}
"print" 	{ return new_symbol(sym.PRINT, yytext()); }
"return" 	{ return new_symbol(sym.RETURN, yytext()); }
"void" 		{ return new_symbol(sym.VOID, yytext()); }
"read"    { return new Symbol(sym.READ, yytext()); }
"new"    { return new Symbol(sym.NEW, yytext()); }
"break"    { return new Symbol(sym.BREAK, yytext()); }
"continue"    { return new Symbol(sym.CONTINUE); }
"if"      { return new_symbol(sym.IF, yytext()); }
"else"    { return new_symbol(sym.ELSE, yytext()); }
"true"   { return new_symbol(sym.BOOLCONST, true); }
"false"  { return new_symbol(sym.BOOLCONST, false); }
"do"   { return new_symbol(sym.DO, yytext()); }
"while"  { return new_symbol(sym.WHILE, yytext()); }
"map"  { return new_symbol(sym.MAP, yytext()); }
"class"  { return new_symbol(sym.CLASS, yytext()); }
"extends"  { return new_symbol(sym.EXTENDS, yytext()); }
"set"  { return new_symbol(sym.SET, yytext()); }
"union"  { return new_symbol(sym.UNION, yytext()); }
"const"  { return new_symbol(sym.CONST, yytext()); }
"interface"  { return new_symbol(sym.INTERFACE, yytext()); }
"&&"      { return new Symbol(sym.AND, yytext()); }
"||"      { return new Symbol(sym.OR, yytext()); }
"=="      { return new Symbol(sym.EQ, yytext()); }
"!="      { return new Symbol(sym.NEQ, yytext()); }
"<="      { return new Symbol(sym.LE, yytext()); }
">="      { return new Symbol(sym.GE, yytext()); }
"<"       { return new Symbol(sym.LT, yytext()); }
">"       { return new Symbol(sym.GT, yytext()); }
"++"      { return new Symbol(sym.INCR, yytext()); }
"--"      { return new Symbol(sym.DECR, yytext()); }
"+" 		{ return new_symbol(sym.PLUS, yytext()); }
"=" 		{ return new_symbol(sym.EQUAL, yytext()); }
";" 		{ return new_symbol(sym.SEMI, yytext()); }
"," 		{ return new_symbol(sym.COMMA, yytext()); }
"(" 		{ return new_symbol(sym.LPAREN, yytext()); }
")" 		{ return new_symbol(sym.RPAREN, yytext()); }
"{" 		{ return new_symbol(sym.LBRACE, yytext()); }
"}"			{ return new_symbol(sym.RBRACE, yytext()); }
"-"			{ return new_symbol(sym.MINUS, yytext()); }
"*"			{ return new_symbol(sym.TIMES, yytext()); }
"/"			{ return new_symbol(sym.DIV, yytext()); }
"%"			{ return new_symbol(sym.MOD, yytext()); }
"["       { return new Symbol(sym.LBRACKET, yytext()); }
"]"       { return new Symbol(sym.RBRACKET, yytext()); }
"."     { return new Symbol(sym.DOT, yytext()); }

"//" {yybegin(COMMENT);}
<COMMENT> . {yybegin(COMMENT);}
<COMMENT> "\r\n" { yybegin(YYINITIAL); }

[0-9]+  { return new_symbol(sym.NUMBER, new Integer (yytext())); }
'\''([^\\'\n]|\\[nrt'\\])'\'' {
    String txt = yytext().substring(1, yytext().length() - 1);
    char c;
    if (txt.length() == 1) {
        c = txt.charAt(0);
    } else {
        switch (txt.charAt(1)) {
            case 'n': c = '\n'; break;
            case 'r': c = '\r'; break;
            case 't': c = '\t'; break;
            case '\'': c = '\''; break;
            case '\\': c = '\\'; break;
            default:
                System.err.println("Nepoznata escape sekvenca: \\" + txt.charAt(1));
                c = '?';
        }
    }

    System.out.println("Prepoznat CHARCONST: '" + c + "' u liniji " + (yyline + 1)); // ← dodaj ovo
    return new_symbol(sym.CHARCONST, c);
}
'[^']' {
    System.err.println("DEBUG: Upao u fallback za CHARCONST: " + yytext());
    return new_symbol(sym.CHARCONST, yytext().charAt(1));
}
([a-z]|[A-Z])[a-z|A-Z|0-9|_]* 	{return new_symbol (sym.IDENT, yytext()); }

. { System.err.println("Leksicka greska ("+yytext()+") u liniji "+(yyline+1)); }










