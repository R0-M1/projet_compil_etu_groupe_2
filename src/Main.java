import Type.Type;
import Type.UnknownType;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Stack;

public class Main {
	public static void main(String[] args) throws Exception {
		// Définir le code source à analyser
		// Lire le contenu du fichier input.txt dans une chaîne de caractères
		String filePath = "src\\input"; // Chemin relatif ou absolu du fichier
		String testCode = new String(Files.readAllBytes(Paths.get(filePath))); // dernier bloc archivé donc au dessus de la pile est celui de core function
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
		System.out.println("Pile des tables des types après la visite :");
		Stack<Map<UnknownType, Type>> typeScopes = visitor.getTypeScopes();

		// Afficher les scopes archivés
		visitor.printArchivedScopes();
	}
}
