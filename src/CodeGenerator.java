import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import Asm.*;

import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import Type.Type;
import Type.UnknownType;
import Asm.Instruction;




public class CodeGenerator  extends AbstractParseTreeVisitor<Program> implements grammarTCLVisitor<Program> {


    private Stack<Map<String, Type>> typeScopes;
    private int nextRegister = 1;
    private int nextLabel = 0;
    private Stack<Map<String, Integer>> registerScopes = new Stack<>();

    public CodeGenerator(Stack<Map<String, Type>> typeScopes) {
        this.typeScopes = typeScopes;
        this.registerScopes.push(new HashMap<>());
    }

    /**
     * Associe un registre unique à une variable dans le scope courant.
     *
     * @param varName Le nom de la variable.
     * @return Le numéro de registre associé à cette variable.
     */
    private int assignRegister(String varName) {
        Map<String, Integer> currentScope = registerScopes.peek();
        if (!currentScope.containsKey(varName)) {
            currentScope.put(varName, nextRegister++);
        }
        return currentScope.get(varName);
    }

    /**
     * Recherche le registre associé à une variable en remontant les scopes.
     *
     * @param varName Le nom de la variable.
     * @return Le numéro de registre associé à cette variable.
     * @throws RuntimeException si la variable n'est pas définie.
     */
    private int lookupRegister(String varName) {
        for (int i = registerScopes.size() - 1; i >= 0; i--) {
            Map<String, Integer> scope = registerScopes.get(i);
            if (scope.containsKey(varName)) {
                return scope.get(varName);
            }
        }
        throw new RuntimeException("Variable non définie : " + varName);
    }

    /**
     * Ouvre un nouveau scope (nouveau bloc).
     */
    private void enterScope() {
        registerScopes.push(new HashMap<>());
    }

    /**
     * Quitte le scope courant (bloc).
     */
    private void exitScope() {
        if (registerScopes.isEmpty()) {
            throw new RuntimeException("Tentative de sortir d'un scope inexistant !");
        }
        registerScopes.pop();
    }



    /**
     * Génère un label unique.
     *
     * @param prefix Le préfixe à utiliser pour le label.
     * @return Un label unique sous forme de chaîne.
     */
    private String generateLabel(String prefix) {
        return prefix + "_" + (nextLabel++);
    }


    /**
     * Initialise un registre donné avec une valeur spécifique.
     * Cette méthode utilise deux instructions pour initialiser le registre :
     * - Une opération XOR pour réinitialiser le registre à 0.
     * - Une opération ADD pour y ajouter la valeur spécifiée.
     *
     * @param register Le numéro du registre à initialiser.
     * @param value    La valeur à placer dans le registre.
     * @return Un programme contenant les instructions nécessaires pour initialiser le registre.
     */
    private Program setRegisterTo(int register, int value) {
        Program program = new Program();

        program.addInstruction(new UAL(UAL.Op.XOR, register, register, register));
        program.addInstruction(new UALi(UALi.Op.ADD, register, register, value));

        return program;
    }

    /**
     * Visite un nœud correspondant à un entier dans l'arbre syntaxique abstrait (AST).
     * Cette méthode extrait la valeur entière du contexte, alloue un registre pour la stocker,
     * et génère les instructions nécessaires pour initialiser le registre avec cette valeur.
     *
     * @param ctx Le contexte de l'entier, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour initialiser un registre avec la valeur de l'entier.
     */
    @Override
    public Program visitInteger(grammarTCLParser.IntegerContext ctx) {
        System.out.println("visitInt");
        Program program = new Program();
        int value = Integer.parseInt(ctx.INT().getText());
        int register = nextRegister++;


        program.addInstructions(setRegisterTo(register, value));

        return program;
    }

    /**
     * Visite un nœud correspondant à un booléen dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode extrait la valeur booléenne du contexte, alloue un registre pour la stocker,
     * et génère les instructions nécessaires pour initialiser le registre avec cette valeur.
     * La valeur booléenne "true" est convertie en 1 et "false" en 0.
     *
     * @param ctx Le contexte du booléen, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour initialiser un registre avec la valeur booléenne.
     */
    @Override
    public Program visitBoolean(grammarTCLParser.BooleanContext ctx) {
        System.out.println("visitBool");
        Program program = new Program();
        int register = nextRegister++;
        int value = ctx.BOOL().getText().equals("true") ? 1 : 0;


        program.addInstructions(setRegisterTo(register, value));

        return program;
    }

    /**
     * Visite un nœud correspondant à une variable dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode récupère le nom de la variable, tente de rechercher le registre associé à cette variable,
     * et génère les instructions nécessaires pour charger la valeur de la variable dans un registre. Si la variable
     * n'est pas trouvée dans le registre, une nouvelle instruction est générée pour l'initialiser avec une valeur par défaut.
     *
     * @param ctx Le contexte de la variable, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions nécessaires pour charger ou initialiser la variable.
     * @throws RuntimeException Si la variable n'est pas définie dans le scope actuel et ne peut pas être initialisée.
     */
    @Override
    public Program visitVariable(grammarTCLParser.VariableContext ctx) {
        System.out.println("visitVariable");


        String varName = ctx.VAR().getText();
        Program program = new Program();

        try {

            int variableRegister = lookupRegister(varName);


            program.addInstruction(new UALi(UALi.Op.ADD, nextRegister, variableRegister, 0));


            nextRegister++;
        } catch (RuntimeException e) {

            int variableRegister = assignRegister(varName);


            program.addInstruction(new UALi(UALi.Op.ADD, nextRegister, 0, 0));
        }

        return program;
    }


    /**
     * Visite un nœud correspondant à une addition dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour additionner les résultats
     * de deux sous-expressions. Elle évalue d'abord l'expression gauche et l'expression droite,
     * puis combine leurs résultats dans un nouveau registre en utilisant l'instruction `ADD`.
     *
     * @param ctx Le contexte de l'addition, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour effectuer l'addition.
     */
    @Override
    public Program visitAddition(grammarTCLParser.AdditionContext ctx) {
        System.out.println("visitAdd");

        Program leftProgram = visit(ctx.expr(0));
        int leftRegister = nextRegister - 1;


        Program rightProgram = visit(ctx.expr(1));
        int rightRegister = nextRegister - 1;

        Program program = new Program();
        program.addInstructions(leftProgram);
        program.addInstructions(rightProgram);

        program.addInstruction(new UAL(UAL.Op.ADD, nextRegister, leftRegister, rightRegister));
        nextRegister++;

        return program;
    }

    /**
     * Visite un nœud correspondant à une multiplication dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour multiplier les résultats
     * de deux sous-expressions. Elle évalue d'abord l'expression gauche et l'expression droite,
     * puis combine leurs résultats dans un nouveau registre en utilisant l'instruction `MUL`.
     *
     * @param ctx Le contexte de la multiplication, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour effectuer la multiplication.
     */
    @Override
    public Program visitMultiplication(grammarTCLParser.MultiplicationContext ctx) {
        System.out.println("visitMul");

        Program leftProgram = visit(ctx.expr(0));
        int leftRegister = nextRegister - 1;


        Program rightProgram = visit(ctx.expr(1));
        int rightRegister = nextRegister - 1;


        Program program = new Program();
        program.addInstructions(leftProgram);
        program.addInstructions(rightProgram);


        program.addInstruction(new UAL(UAL.Op.MUL, nextRegister, leftRegister, rightRegister));
        nextRegister++;

        return program;
    }

    /**
     * Visite un nœud correspondant à une opération d'opposé arithmétique (négation) dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour calculer l'opposé d'une expression
     * en soustrayant la valeur de l'expression de 0 (i.e., `-x`).
     *
     * @param ctx Le contexte de l'opération d'opposé, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour calculer l'opposé de l'expression.
     */
    @Override
    public Program visitOpposite(grammarTCLParser.OppositeContext ctx) {
        System.out.println("visitOpposite");

        Program childProgram = visit(ctx.expr());
        int childRegister = nextRegister - 1;


        Program program = new Program();
        program.addInstructions(childProgram);


        program.addInstruction(new UAL(UAL.Op.SUB, nextRegister, 0, childRegister));
        nextRegister++;

        return program;
    }

    /**
     * Visite un nœud correspondant à une négation logique dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour calculer la négation logique d'une expression.
     * La négation logique est réalisée en effectuant un XOR entre la valeur de l'expression et 1.
     *
     * @param ctx Le contexte de la négation logique, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour calculer la négation logique.
     */
    @Override
    public Program visitNegation(grammarTCLParser.NegationContext ctx) {
        System.out.println("visitNegation");

        Program childProgram = visit(ctx.expr());
        int childRegister = nextRegister - 1;


        Program program = new Program();
        program.addInstructions(childProgram);


        program.addInstruction(new UALi(UALi.Op.XOR, nextRegister, childRegister, 1));
        nextRegister++;

        return program;
    }

    /**
     * Visite un nœud correspondant à une opération logique AND dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour effectuer une opération logique AND
     * entre les résultats de deux sous-expressions. Elle évalue d'abord les sous-expressions gauche
     * et droite, puis applique l'opération AND sur leurs valeurs.
     *
     * @param ctx Le contexte de l'opération AND, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour calculer l'opération logique AND.
     */
    @Override
    public Program visitAnd(grammarTCLParser.AndContext ctx) {
        System.out.println("visitAnd");
        Program program = new Program();


        Program leftProgram = visit(ctx.expr(0));
        program.addInstructions(leftProgram);
        int leftRegister = nextRegister - 1;


        Program rightProgram = visit(ctx.expr(1));
        program.addInstructions(rightProgram);
        int rightRegister = nextRegister - 1;


        program.addInstruction(new UAL(UAL.Op.AND, nextRegister, leftRegister, rightRegister));
        nextRegister++;

        return program;
    }

    /**
     * Visite un nœud correspondant à une opération logique OR dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour effectuer une opération logique OR
     * entre les résultats de deux sous-expressions. Elle évalue d'abord les sous-expressions gauche
     * et droite, puis applique l'opération OR sur leurs valeurs.
     *
     * @param ctx Le contexte de l'opération OR, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour calculer l'opération logique OR.
     */
    public Program visitOr(grammarTCLParser.OrContext ctx) {
        System.out.println("visitOr");

        Program program = new Program();


        Program leftProgram = visit(ctx.expr(0));
        int leftRegister = nextRegister - 1;


        Program rightProgram = visit(ctx.expr(1));
        int rightRegister = nextRegister - 1;


        program.addInstructions(leftProgram);
        program.addInstructions(rightProgram);


        int resultRegister = nextRegister++;


        program.addInstruction(new UAL(UAL.Op.OR , resultRegister, leftRegister,  rightRegister));

        return program;
    }

    /**
     * Visite un nœud correspondant à une opération de comparaison dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour évaluer une comparaison entre deux expressions
     * (par exemple `<`, `>`, `<=`, `>=`). Elle produit un résultat booléen (0 ou 1) qui est stocké
     * dans un registre.
     *
     * @param ctx Le contexte de la comparaison, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour évaluer la comparaison.
     * @throws RuntimeException si l'opérateur de comparaison n'est pas pris en charge.
     */
    @Override
    public Program visitComparison(grammarTCLParser.ComparisonContext ctx) {
        System.out.println("visitComparaison");
        Program program = new Program();


        Program leftProgram = visit(ctx.expr(0));
        int leftRegister = nextRegister - 1;


        Program rightProgram = visit(ctx.expr(1));
        int rightRegister = nextRegister - 1;


        program.addInstructions(leftProgram);
        program.addInstructions(rightProgram);


        int resultRegister = nextRegister++;


        String labelTrue = generateLabel("LABEL_TRUE_") ;
        String labelEnd = "LABEL_FALSE_" + nextLabel;


        String operator = ctx.op.getText();
        switch (operator) {
            case "<":
                program.addInstruction(new CondJump(CondJump.Op.JINF,leftRegister,rightRegister,labelTrue));
                break;
            case ">":
                program.addInstruction(new CondJump(CondJump.Op.JSUP,leftRegister,rightRegister,labelTrue));
                break;
            case "<=":
                program.addInstruction(new CondJump(CondJump.Op.JINF,leftRegister,rightRegister,labelTrue));
                program.addInstruction(new CondJump(CondJump.Op.JEQU,leftRegister,rightRegister,labelTrue));
                break;
            case ">=":
                program.addInstruction(new CondJump(CondJump.Op.JSUP,leftRegister,rightRegister,labelTrue));
                program.addInstruction(new CondJump(CondJump.Op.JEQU,leftRegister,rightRegister,labelTrue));
                break;
            default:
                throw new RuntimeException("Opérateur non supporté : " + operator);
        }


        program.addInstruction(new UALi(UALi.Op.ADD,resultRegister , resultRegister ,0));
        program.addInstruction( new JumpCall(JumpCall.Op.JMP,labelEnd));


        program.addInstruction(new UALi(UALi.Op.ADD,resultRegister , resultRegister ,1));


        program.addInstruction(new Instruction(labelEnd, ""){});

        return program;
    }

    /**
     * Visite un nœud correspondant à une opération de comparaison d'égalité ou de différence dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour évaluer une comparaison d'égalité (`==`)
     * ou de différence (`!=`) entre deux expressions. Elle produit un résultat booléen (0 ou 1)
     * qui est stocké dans un registre.
     *
     * @param ctx Le contexte de la comparaison d'égalité, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour évaluer la comparaison.
     * @throws RuntimeException si l'opérateur de comparaison n'est pas pris en charge.
     */
    @Override
    public Program visitEquality(grammarTCLParser.EqualityContext ctx) {
        System.out.println("visitEquality");
        Program program = new Program();


        int resultRegister = nextRegister++;

        Program leftProgram = visit(ctx.expr(0));
        Program rightProgram = visit(ctx.expr(1));

        int leftRegister = nextRegister - 1;
        int rightRegister = nextRegister - 1;


        program.addInstructions(leftProgram);
        program.addInstructions(rightProgram);


        String labelTrue = generateLabel("LABEL_TRUE_") ;
        String labelEnd = "LABEL_END_" + nextLabel;


        String operator = ctx.op.getText();
        switch (operator) {
            case "==":
                program.addInstruction(new CondJump(CondJump.Op.JEQU,leftRegister,rightRegister,labelTrue));
                break;
            case "!=":
                program.addInstruction(new CondJump(CondJump.Op.JNEQ,leftRegister,rightRegister,labelTrue));
                break;
            default:
                throw new RuntimeException("Opérateur non supporté : " + operator);
        }


        program.addInstruction(new UALi(UALi.Op.ADD,resultRegister , resultRegister ,0));
        program.addInstruction( new JumpCall(JumpCall.Op.JMP,labelEnd));


        program.addInstruction(new UALi(UALi.Op.ADD,resultRegister , resultRegister ,1));


        program.addInstruction(new Instruction(labelEnd, ""){});

        return program;

    }


    /**
     * Visite un nœud correspondant à une déclaration de variable dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour déclarer une variable,
     * et, si une initialisation est présente, pour évaluer l'expression assignée à la variable.
     * Si aucune initialisation n'est spécifiée, la variable est initialisée à 0 par défaut.
     *
     * @param ctx Le contexte de la déclaration de variable, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour déclarer et éventuellement initialiser la variable.
     */
    @Override
    public Program visitDeclaration(grammarTCLParser.DeclarationContext ctx) {
        System.out.println("visitDecla");
        Program program = new Program();


        String varName = ctx.VAR().getText();


        int varRegister = assignRegister(varName);



        if (ctx.ASSIGN() != null && ctx.expr() != null) {

            Program exprProgram = visit(ctx.expr());
            program.addInstructions(exprProgram);


            int exprRegister = nextRegister - 1;


            if (exprRegister != varRegister) {
                program.addInstruction(new UALi(UALi.Op.ADD, varRegister, exprRegister, 0));
            }
        } else {


            program.addInstruction(new UAL(UAL.Op.XOR, varRegister, varRegister, varRegister));
        }


        return program;
    }


    /**
     * Visite un nœud correspondant à une affectation dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour affecter une valeur à une variable.
     * Elle vérifie que la variable a été déclarée, évalue l'expression assignée et met à jour
     * le registre associé à la variable.
     *
     * @param ctx Le contexte de l'affectation, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour effectuer l'affectation.
     * @throws IllegalStateException si la variable n'a pas été déclarée ou si un registre valide pour le résultat de l'expression est introuvable.
     */
    @Override
    public Program visitAssignment(grammarTCLParser.AssignmentContext ctx) {
        System.out.println("visitIF");
        Program program = new Program();


        String variableName = ctx.VAR().getText();


        try {
            lookupRegister(variableName);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Variable non déclarée : " + variableName, e);
        }


        Program expressionProgram = visit(ctx.expr(0));
        program.addInstructions(expressionProgram);


        int expressionRegister = nextRegister - 1;
        if (expressionRegister < 0) {
            throw new IllegalStateException("Aucun registre valide pour stocker le résultat de l'expression.");
        }


        int variableRegister;
        if (registerScopes.peek().containsKey(variableName)) {
            variableRegister = registerScopes.peek().get(variableName);
        } else {
            variableRegister = assignRegister(variableName);
        }


        program.addInstruction(new UALi(UALi.Op.ADD, variableRegister, expressionRegister, 0));

        return program;
    }


    @Override
    public Program visitTab_initialization(grammarTCLParser.Tab_initializationContext ctx) {
        throw new UnsupportedOperationException("Unimplemented method 'visitMain'");
    }


    @Override
    public Program visitTab_access(grammarTCLParser.Tab_accessContext ctx) {
        throw new UnsupportedOperationException("Unimplemented method 'visitMain'");
    }


    /**
     * Visite un nœud correspondant à une instruction conditionnelle `if` dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour évaluer une condition et exécuter
     * un bloc d'instructions si la condition est vraie. Si une clause `else` est présente,
     * elle génère également les instructions pour l'exécuter lorsque la condition est fausse.
     *
     * @param ctx Le contexte de l'instruction `if`, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour évaluer et exécuter l'instruction conditionnelle.
     * @throws IllegalStateException si le registre contenant le résultat de la condition n'est pas valide.
     */
    @Override
    public Program visitIf(grammarTCLParser.IfContext ctx) {
        System.out.println("visitIF");
        Program program = new Program();


        String elseLabel = generateLabel("IF_ELSE");
        String endLabel = generateLabel("IF_END");

        Program conditionProgram = visit(ctx.expr());
        program.addInstructions(conditionProgram);


        int conditionRegister = nextRegister - 1;
        if (conditionRegister < 0) {
            throw new IllegalStateException("Impossible de récupérer un registre valide pour la condition.");
        }


        program.addInstruction(new Instruction(null, "JEQ R" + conditionRegister + " 0 " + elseLabel) {
        });


        Program ifProgram = visit(ctx.instr(0));
        program.addInstructions(ifProgram);


        if (ctx.ELSE() != null) {
            program.addInstruction(new Instruction(null, "JMP " + endLabel) {
            });
        }


        program.addInstruction(new Instruction(elseLabel + ":", null) {
        });


        if (ctx.instr().size() > 1) {
            Program elseProgram = visit(ctx.instr(1));
            program.addInstructions(elseProgram);
        }


        program.addInstruction(new Instruction(endLabel + ":", null) {
        });

        return program;
    }


    /**
     * Visite un nœud correspondant à une boucle `while` dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour évaluer une condition et exécuter
     * de manière répétée un bloc d'instructions tant que la condition est vraie. Si la condition
     * est fausse, le flux sort de la boucle.
     *
     * @param ctx Le contexte de la boucle `while`, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour exécuter la boucle `while`.
     * @throws IllegalStateException si le registre contenant le résultat de la condition n'est pas valide.
     */
    @Override
    public Program visitWhile(grammarTCLParser.WhileContext ctx) {
        System.out.println("visitWhile");

        Program program = new Program();


        String loopStartLabel = generateLabel("WHILE_START");
        String loopEndLabel = "WHILE_END_" + nextLabel;


        program.addInstruction(new Instruction(loopStartLabel + ":", null) {
        });


        Program conditionProgram = visit(ctx.expr());
        program.addInstructions(conditionProgram);


        int conditionRegister = nextRegister - 1;
        if (conditionRegister < 0) {
            throw new IllegalStateException("Impossible de récupérer un registre valide pour la condition.");
        }


        program.addInstruction(new Instruction(null, "JEQ R" + conditionRegister + " 0 " + loopEndLabel) {
        });


        Program bodyProgram = visit(ctx.instr());
        program.addInstructions(bodyProgram);


        program.addInstruction(new Instruction(null, "JMP " + loopStartLabel) {
        });


        program.addInstruction(new Instruction(loopEndLabel + ":", null) {
        });

        return program;


    }


    /**
     * Visite un nœud correspondant à une boucle `for` dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour exécuter une boucle `for`,
     * incluant l'initialisation, la condition, l'incrémentation et le corps de la boucle.
     * Elle suit la structure standard d'une boucle `for` :
     *
     * ```
     * for (initialization; condition; increment) {
     *     body;
     * }
     * ```
     *
     * @param ctx Le contexte de la boucle `for`, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour exécuter la boucle `for`.
     * @throws IllegalStateException si le registre contenant le résultat de la condition n'est pas valide.
     */
    @Override
    public Program visitFor(grammarTCLParser.ForContext ctx) {
        System.out.println("visitFor");
        Program program = new Program();


        String loopStartLabel = generateLabel("FOR_START");
        String loopEndLabel = "FOR_END" + nextLabel;


        if (ctx.instr(0) != null) {
            Program initializationProgram = visit(ctx.instr(0));
            program.addInstructions(initializationProgram);
        }


        program.addInstruction(new Instruction(loopStartLabel + ":", null) {
        });


        Program conditionProgram = visit(ctx.expr());
        program.addInstructions(conditionProgram);


        int conditionRegister = nextRegister - 1;
        if (conditionRegister < 0) {
            throw new IllegalStateException("Impossible de récupérer un registre valide pour la condition.");
        }


        program.addInstruction(new Instruction(null, "JEQ R" + conditionRegister + " 0 " + loopEndLabel) {
        });


        if (ctx.instr(2) != null) {
            Program bodyProgram = visit(ctx.instr(2));
            program.addInstructions(bodyProgram);
        }


        if (ctx.instr(1) != null) {
            Program incrementProgram = visit(ctx.instr(1));
            program.addInstructions(incrementProgram);
        }


        program.addInstruction(new Instruction(null, "JMP " + loopStartLabel) {
        });


        program.addInstruction(new Instruction(loopEndLabel + ":", null) {
        });

        return program;


    }


    /**
     * Visite un nœud correspondant à un bloc d'instructions dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode gère l'ouverture et la fermeture d'un nouveau scope pour le bloc. Elle visite
     * chaque instruction contenue dans le bloc, génère les instructions correspondantes,
     * et les combine en un programme global.
     *
     * @param ctx Le contexte du bloc, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions combinées pour toutes les instructions du bloc.
     */
    @Override
    public Program visitBlock(grammarTCLParser.BlockContext ctx) {
        System.out.println("visitBlock");

        enterScope();

        Program program = new Program();


        for (grammarTCLParser.InstrContext instructionCtx : ctx.instr()) {

            Program instructionProgram = visit(instructionCtx);


            program.addInstructions(instructionProgram);
        }


        exitScope();


        return program;
    }

    /**
     * Visite un nœud correspondant à une instruction `print` dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour évaluer l'expression à afficher
     * et ajouter une instruction d'entrée/sortie (I/O) pour imprimer le résultat sur la sortie standard.
     *
     * @param ctx Le contexte de l'instruction `print`, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour évaluer l'expression et imprimer son résultat.
     */
    @Override
    public Program visitPrint(grammarTCLParser.PrintContext ctx) {
        System.out.println("visitPrint");
        Program program = new Program();


        Program exprProgram = visit(ctx.VAR());


        program.addInstructions(exprProgram);


        int resultRegister = nextRegister - 1;


        program.addInstruction(new IO(IO.Op.PRINT, resultRegister));

        return program;
    }


    /**
     * Visite un nœud correspondant à une instruction `return` dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour gérer une instruction de retour dans une fonction.
     * Elle évalue l'expression de retour, place sa valeur dans le registre conventionnel pour les retours (`R0`),
     * et ajoute une instruction `RET` pour signaler la fin de la fonction.
     *
     * @param ctx Le contexte de l'instruction `return`, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour évaluer l'expression de retour et terminer la fonction.
     */
    @Override
    public Program visitReturn(grammarTCLParser.ReturnContext ctx) {
        System.out.println("visitReturn");

        Program program = new Program();


        if (ctx.expr() instanceof grammarTCLParser.VariableContext) {

            grammarTCLParser.VariableContext varCtx = (grammarTCLParser.VariableContext) ctx.expr();
            String varName = varCtx.VAR().getText();
            int resultRegister = lookupRegister(varName);


            if (resultRegister != 0) {
                program.addInstruction(new UAL(UAL.Op.ADD, 0, resultRegister, 0));
            }
        } else {

            Program exprProgram = visit(ctx.expr());
            int resultRegister = nextRegister - 1;


            program.addInstructions(exprProgram);


            if (resultRegister != 0) {
                program.addInstruction(new UAL(UAL.Op.ADD, 0, resultRegister, 0));
            }
        }


        program.addInstruction(new Ret());


        return program;
    }




    /**
     * Visite un nœud correspondant à une déclaration de fonction dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour gérer une fonction, y compris
     * les étiquettes de début et de fin, le chargement des arguments dans les registres,
     * et l'exécution du corps de la fonction. Si une expression de retour est spécifiée,
     * elle génère également les instructions pour retourner une valeur.
     *
     * @param ctx Le contexte de la déclaration de fonction, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour définir et exécuter la fonction.
     */
    @Override
    public Program visitDecl_fct(grammarTCLParser.Decl_fctContext ctx) {
        System.out.println("Decl_fct");
        Program program = new Program();


        String functionName = ctx.VAR(0).getText();


        program.addInstruction(new Instruction(functionName + ":", "") {
        });


        List<TerminalNode> arguments = ctx.VAR().subList(1, ctx.VAR().size());
        int argCount = arguments.size();
        for (int i = 0; i < argCount; i++) {
            String argName = arguments.get(i).getText();
            program.addInstruction(new Instruction(null, "LD R" + i + " " + argName) {
            });
        }


        Program bodyProgram = visit(ctx.core_fct());
        program.addInstructions(bodyProgram);


        if (ctx.type(0) != null && ctx.core_fct().expr() != null) {

            Program returnProgram = visit(ctx.core_fct().expr());
            int returnRegister = nextRegister - 1;
            program.addInstructions(returnProgram);
            program.addInstruction(new Instruction(null, "RET R" + returnRegister) {
            });
        } else {

            program.addInstruction(new Instruction(null, "RET") {
            });
        }


        String endLabel = "END_" + functionName;
        program.addInstruction(new Instruction(endLabel + ":", "") {
        });

        return program;
    }


    /**
     * Visite un nœud correspondant au corps d'une fonction dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions pour exécuter les instructions contenues dans le corps de la fonction.
     * Si une expression de retour est spécifiée, elle évalue l'expression, place son résultat dans le registre
     * de retour conventionnel (`R0`), et ajoute une instruction `RET` pour signaler la fin de la fonction.
     *
     * @param ctx Le contexte du corps de la fonction, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions pour exécuter le corps de la fonction.
     */
    @Override
    public Program visitCore_fct(grammarTCLParser.Core_fctContext ctx) {
        System.out.println("Core_fct");

        Program program = new Program();


        for (grammarTCLParser.InstrContext instrCtx : ctx.instr()) {

            Program instructionProgram = visit(instrCtx);
            program.addInstructions(instructionProgram);
        }


        if (ctx.expr() != null) {

            Program returnExprProgram = visit(ctx.expr());
            program.addInstructions(returnExprProgram);



            program.addInstruction(new UALi(UALi.Op.ADD, 0, this.nextRegister - 1, 0));
        }


        program.addInstruction(new Ret());


        return program;
    }

    /**
     * Visite un nœud correspondant à la fonction principale (`main`) dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode génère les instructions nécessaires pour exécuter le programme principal.
     * Elle parcourt les déclarations de fonctions, génère le code correspondant, et traite le corps
     * principal du programme s'il est défini.
     *
     * @param ctx Le contexte de la fonction principale, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions combinées pour les déclarations de fonctions
     *         et le corps principal du programme.
     */
    @Override
    public Program visitMain(grammarTCLParser.MainContext ctx) {
        System.out.println("visitMain");
        this.nextRegister=0;
        Program program = new Program();


        for (grammarTCLParser.Decl_fctContext declCtx : ctx.decl_fct()) {

            Program declProgram = visit(declCtx);
            program.addInstructions(declProgram);
        }


        if (ctx.core_fct() != null) {
            Program coreProgram = visit(ctx.core_fct());
            program.addInstructions(coreProgram);
        }


        return program;
    }



    /**
     * Visite un nœud correspondant à un type de base (`int` ou `bool`) dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode analyse le type spécifié et génère le programme correspondant.
     * Si le type n'est pas pris en charge, une exception est levée.
     *
     * @param ctx Le contexte du type de base, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions associées au type de base.
     * @throws RuntimeException si le type n'est pas pris en charge.
     */
    @Override
    public Program visitBase_type(grammarTCLParser.Base_typeContext ctx) {
        System.out.println("visitBase_type");
        String type = ctx.getText();
        Program program = new Program();

        switch (type) {
            case "int":
                program = visit(ctx);
                break;
            case "bool":
                program = visit(ctx);
                break;
            default:
                throw new RuntimeException("Opérateur non supporté : " + type + " veuillez choisir int ou bool");
        }

        return program;
    }

    @Override
    public Program visitTab_type(grammarTCLParser.Tab_typeContext ctx) {
        throw new UnsupportedOperationException("Unimplemented method 'visitTab_type'");
    }


    /**
     * Visite un nœud correspondant à une expression entre parenthèses dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode permet de traiter les expressions encapsulées dans des parenthèses
     * en délégant directement leur évaluation à l'expression interne.
     *
     * @param ctx Le contexte de l'expression entre parenthèses, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions générées pour l'expression interne.
     */
    @Override
    public Program visitBrackets(grammarTCLParser.BracketsContext ctx) {
        System.out.println("visitBrackets");
        return visit(ctx.expr());
    }

    /**
     * Visite un nœud correspondant à une expression entre parenthèses dans l'arbre syntaxique abstrait (AST).
     *
     * Cette méthode est une tentative ( version non aboutie ) mais elle devrait permetre de traiter les expressions encapsulées dans des parenthèses
     * en délégant directement leur évaluation à l'expression interne.
     *
     * @param ctx Le contexte de l'expression entre parenthèses, fourni par l'analyseur syntaxique.
     * @return Un programme contenant les instructions générées pour l'expression interne.
     */
    @Override
    public Program visitCall(grammarTCLParser.CallContext ctx) {
        System.out.println("visitCall");

        Program program = new Program();


        String functionName = ctx.VAR().getText();
        int totalChildren = ctx.getChildCount();


        int argumentCount = (totalChildren == 3) ? 0 : (totalChildren - 2) / 2;


        List<Integer> argRegisters = new ArrayList<>();


        for (int index = 0; index < argumentCount; index++) {
            Program exprProgram = visit(ctx.getChild(2 + (2 * index)));
            program.addInstructions(exprProgram);
            argRegisters.add(this.nextRegister - 1);
        }


        for (int reg : argRegisters) {
            program.addInstruction(new UALi(UALi.Op.ADD, this.nextRegister++, reg, 0));
        }

        program.addInstruction(new JumpCall(JumpCall.Op.CALL, functionName));

        program.addInstruction(new UALi(UALi.Op.ADD, this.nextRegister, this.nextRegister - 1, 0));
        this.nextRegister++;

        return program;
    }

}