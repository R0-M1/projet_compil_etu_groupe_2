import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;


public class Main {
	public static void main(String[] args) {
		// Définir le code source à analyser
		String testCode = "int main() {bool x; x=true; if(x){int x; x=5;if(x==5){int x; x=5;}} return x;}";
		System.out.println("Code source :\n" + testCode);

		// Charger le code source en tant que flux de caractères
		CharStream input = CharStreams.fromString(testCode);

		// Analyse lexicale
		grammarTCLLexer lexer = new grammarTCLLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);

		// Analyse syntaxique
		grammarTCLParser parser = new grammarTCLParser(tokens);
		grammarTCLParser.MainContext tree = parser.main();

		// Afficher l'arbre syntaxique
		System.out.println("Arbre syntaxique généré :");
		System.out.println(tree.toStringTree(parser));

		// Visiter l'AST avec le TyperVisitor
		TyperVisitor visitor = new TyperVisitor();
		visitor.visitMain(tree);

		// Afficher les types enregistrés après la visite
		System.out.println("Table des types après la visite :");
		System.out.println(visitor.getTypes());
	}
};
