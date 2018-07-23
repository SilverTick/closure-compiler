/*
 * Copyright 2011 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.AstValidator.ViolationHandler;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.Token;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public final class AstValidatorTest extends CompilerTestCase {

  private boolean lastCheckWasValid = true;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return createValidator(compiler);
  }

  private AstValidator createValidator(Compiler compiler) {
    lastCheckWasValid = true;
    return new AstValidator(
        compiler,
        new ViolationHandler() {
          @Override
          public void handleViolation(String message, Node n) {
            lastCheckWasValid = false;
          }
        },
        /* validateScriptFeatures= */ true);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    disableAstValidation();
    disableNormalize();
    disableLineNumberCheck();
  }

  public void testClass() {
    valid(lines(
        "class C {",
        "  get m1() {return 1}",
        "  set m1(a) {}",
        "  m2(a) {}",
        "  ['m2']() {}",
        "  [m2]() {}",
        "}"));

    Node c = new Node(Token.CLASS, IR.name("C"), IR.empty());
    Node members = new Node(Token.CLASS_MEMBERS);
    c.addChildToBack(members);
    expectValid(c, Check.STATEMENT);
    Node method1 = new Node(
        Token.MEMBER_FUNCTION_DEF, IR.function(IR.name(""), IR.paramList(), IR.block()));
    members.addChildToBack(method1);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid empty string
    Node method2 = Node.newString(Token.MEMBER_FUNCTION_DEF, "");
    method2.addChildToBack(IR.function(IR.name(""), IR.paramList(), IR.block()));
    members.addChildToBack(method2);

    expectInvalid(c, Check.STATEMENT);
  }

  public void testForIn() {
    valid("for(var a in b);");
    valid("for(let a in b);");
    valid("for(const a in b);");
    valid("for(a in b);");
    valid("for(a in []);");
    valid("for(a in {});");
  }

  public void testForOf() {
    valid("for(var a of b);");
    valid("for(let a of b);");
    valid("for(const a of b);");
    valid("for(a of b);");
    valid("for(a of []);");
    valid("for(a of {});");
  }

  public void testForAwaitOf() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    valid("for await(var a of b);");
    valid("for await(let a of b);");
    valid("for await(const a of b);");
    valid("for await(a of b);");
    valid("for await(a of []);");
    valid("for await(a of {});");
  }

  public void testQuestionableForIn() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    setExpectParseWarningsThisTest();
    valid("for(var a = 1 in b);");
  }

  public void testDebugger() {
    valid("debugger;");
  }

  public void testValidScript() {
    Node n = new Node(Token.SCRIPT);
    expectInvalid(n, Check.SCRIPT);
    n.setInputId(new InputId("something_input"));
    n.setStaticSourceFile(new SimpleSourceFile("something", false));
    expectValid(n, Check.SCRIPT);
    expectInvalid(n, Check.STATEMENT);
    expectInvalid(n, Check.EXPRESSION);
  }

  public void testValidStatement1() {
    Node n = new Node(Token.RETURN);
    expectInvalid(n, Check.EXPRESSION);
    expectValid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  public void testValidExpression1() {
    Node n = new Node(Token.ARRAYLIT, new Node(Token.EMPTY));
    expectValid(n, Check.EXPRESSION);
    expectInvalid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  public void testValidExpression2() {
    Node n = new Node(Token.NOT, new Node(Token.TRUE));
    expectValid(n, Check.EXPRESSION);
    expectInvalid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  public void testValidConst() {
    valid("const x = r;");
    valid("const [x] = r;");
    valid("const {x} = r;");
    valid("const x = r, y = r;");
    valid("const {x} = r, y = r;");
    valid("const x = r, {y} = r;");
  }

  public void testInvalidConst() {
    Node n = new Node(Token.CONST);
    expectInvalid(n, Check.STATEMENT);

    n.addChildToBack(IR.name("x"));
    expectInvalid(n, Check.STATEMENT);

    n = new Node(Token.CONST);
    n.addChildToBack(new Node(Token.DESTRUCTURING_LHS));
    n.getFirstChild().addChildToBack(new Node(Token.OBJECT_PATTERN));

    expectInvalid(n, Check.STATEMENT);
  }

  public void testInvalidConstLanguageLevel() {
    Node n = IR.constNode(IR.name("x"), IR.number(3));

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    expectInvalid(n, Check.STATEMENT);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectValid(n, Check.STATEMENT);
  }

  public void testInvalidLetLanguageLevel() {
    Node n = IR.let(IR.name("x"), IR.number(3));

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    expectInvalid(n, Check.STATEMENT);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectValid(n, Check.STATEMENT);
  }

  public void testNewTargetIsValidExpression() {
    Node n = new Node(Token.NEW_TARGET);
    expectValid(n, Check.EXPRESSION);
  }

  public void testCastOnLeftSideOfAssign() {
    JSDocInfoBuilder jsdoc = new JSDocInfoBuilder(false);
    jsdoc.recordType(new JSTypeExpression(IR.string("number"), "<AstValidatorTest>"));
    Node n = IR.exprResult(
        new Node(
            Token.ASSIGN,
            IR.cast(IR.name("x"), jsdoc.build()),
            IR.number(0)));
    expectValid(n, Check.STATEMENT);
  }

  public void testInvalidEmptyStatement() {
    Node n = new Node(Token.EMPTY, new Node(Token.TRUE));
    expectInvalid(n, Check.STATEMENT);
    n.detachChildren();
    expectValid(n, Check.STATEMENT);
  }

  public void testInvalidNumberStatement() {
    Node n = IR.number(1);
    expectInvalid(n, Check.STATEMENT);
    n = IR.exprResult(n);
    expectValid(n, Check.STATEMENT);
  }

  public void testValidRestParameter() {
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    valid("function f(a,...rest){}");
    valid("function f(a,...[re,...st]){}");
  }

  public void testDefaultParameter() {
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    valid("function f(a = 0, b){}");
  }

  public void testAwaitExpression() {
    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node awaitNode = new Node(Token.AWAIT);
    awaitNode.addChildToBack(IR.number(1));
    Node parentFunction =
        IR.function(IR.name("foo"), IR.paramList(), IR.block(IR.returnNode(awaitNode)));
    parentFunction.setIsAsyncFunction(true);
    expectValid(awaitNode, Check.EXPRESSION);
  }

  public void testAwaitExpressionNonAsyncFunction() {
    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node awaitNode = new Node(Token.AWAIT);
    awaitNode.addChildToBack(IR.number(1));
    Node parentFunction =
        IR.function(IR.name("foo"), IR.paramList(), IR.block(IR.returnNode(awaitNode)));
    parentFunction.setIsAsyncFunction(false);
    expectInvalid(awaitNode, Check.EXPRESSION);
  }

  public void testAwaitExpressionNoFunction() {
    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node n = new Node(Token.AWAIT);
    n.addChildToBack(IR.number(1));
    expectInvalid(n, Check.EXPRESSION);
  }

  public void testInvalidArrayPattern0() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);

    // [...x = 1] = [];
    Node n = IR.assign(
        new Node(Token.ARRAY_PATTERN,
            new Node(Token.REST,
                new Node(Token.DEFAULT_VALUE,
                    IR.name("x"), IR.arraylit()))),
        IR.arraylit());
    expectInvalid(n, Check.EXPRESSION);
  }

  public void testValidDestructuringAssignment0() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    valid("var [x] = obj;");
    valid("var [x = 1] = obj;");
    valid("var [...y] = obj;");
    valid("var [x, ...y] = obj;");

    valid("[x] = [];");
    valid("[x = 1] = [];");
    valid("[x.y] = [];");
    valid("[x.y = 1] = [];");
    valid("[x['y']] = [];");
    valid("[x['y'] = 1] = [];");
    valid("[x().y] = [];");
    valid("[x().y = 1] = [];");
    valid("[x()['y']] = [];");
    valid("[x()['y'] = 1] = [];");

    valid("([...y] = obj);");
    valid("([x, ...y] = obj);");
    valid("([...this.x] = obj);");
    valid("([...this['x']] = obj);");
    valid("([...x.y] = obj);");
    valid("([...x['y']] = obj);");
    valid("([...x().y] = obj);");
    valid("([...x()['y']] = obj);");
  }

  public void testValidDestructuringAssignment1() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    valid("var {a:b} = obj;");
    valid("({a:b} = obj);");
    valid("({a:b.c} = obj);");
    valid("({a:b().c} = obj);");
    valid("({a:b['c']} = obj);");
    valid("({a:b()['c']} = obj);");
    valid("({a:b.c = 1} = obj);");
    valid("({a:b().c = 1} = obj);");
    valid("({a:b['c'] = 1} = obj);");
    valid("({a:b()['c'] = 1} = obj);");
  }

  public void testValidDestructuringAssignment2() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    valid("var {['a']:b} = obj;");
    valid("({['a']:b} = obj);");
    valid("({['a']:this.b} = obj);");
    valid("({['a']:b.c} = obj);");
    valid("({['a']:b.c = 1} = obj);");
    valid("({['a']:b().c} = obj);");
    valid("({['a']:b().c = 1} = obj);");
    valid("({['a']:b['c']} = obj);");
    valid("({['a']:b['c'] = 1} = obj);");
    valid("({['a']:b()['c']} = obj);");
    valid("({['a']:b()['c'] = 1} = obj);");
  }

  public void testObjectRestAssignment() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    valid("var {a, ...rest} = obj;");
    valid("({a:b, ...rest} = obj);");
    valid("({a:b.c, ...rest} = obj);");
    valid("({a:b().c, ...rest} = obj);");
    valid("({a:b['c'], ...rest} = obj);");
    valid("({a:b()['c'], ...rest} = obj);");
    valid("({a:b.c = 1, ...rest} = obj);");
    valid("({a:b().c = 1, ...rest} = obj);");
    valid("({a:b['c'] = 1, ...rest} = obj);");
    valid("({a:b()['c'] = 1, ...rest} = obj);");
  }

  public void testInvalidObjectRestForLanguageLevel() {
    Node n = IR.assign(IR.objectPattern(IR.rest(IR.name("x"))), IR.objectlit());

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectInvalid(n, Check.EXPRESSION);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2018);
    expectValid(n, Check.EXPRESSION);
  }

  public void testInvalidArrayRestForLanguageLevel() {
    Node n = IR.assign(IR.arrayPattern(IR.rest(IR.name("x"))), IR.arraylit());

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    expectInvalid(n, Check.EXPRESSION);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectValid(n, Check.EXPRESSION);
  }

  public void testInvalidDestructuringAssignment() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);

    Node n = IR.assign(
        new Node(Token.OBJECT_PATTERN, new Node(Token.ARRAY_PATTERN)), IR.objectlit());
    expectInvalid(n, Check.EXPRESSION);

    n = IR.assign(
        new Node(Token.ARRAY_PATTERN, IR.computedProp(IR.string("x"), IR.number(1))),
        IR.objectlit());
    expectInvalid(n, Check.EXPRESSION);

    Node stringkey = IR.stringKey("x");
    stringkey.addChildToFront(IR.computedProp(IR.string("x"), IR.number(1)));
    n = IR.assign(new Node(Token.OBJECT_PATTERN, stringkey), IR.objectlit());
    expectInvalid(n, Check.EXPRESSION);
  }

  /** Tests checking that AstValidator validates one particular Feature in the AST. */
  public void testFeatureValidation_getter() {
    testFeatureValidation("var obj = {get f() {}};", Feature.GETTER);
  }

  public void testFeatureValidation_setter() {
    testFeatureValidation("var obj = {set f(x) {}};", Feature.SETTER);
  }

  public void testFeatureValidation_classGetterSetter() {
    testFeatureValidation("class C { get f() {} }", Feature.CLASS_GETTER_SETTER);
    testFeatureValidation("class C { set f(x) {} }", Feature.CLASS_GETTER_SETTER);
  }

  public void testFeatureValidation_classExtends() {
    testFeatureValidation("class C extends B {}", Feature.CLASS_EXTENDS);
  }

  public void testFeatureValidation_arrowFunctions() {
    testFeatureValidation("var arrow = () => 3", Feature.ARROW_FUNCTIONS);
    testFeatureValidation("var asyncArrow = async () => 3", Feature.ARROW_FUNCTIONS);
  }

  public void testFeatureValidation_blockScopedFunctionDeclaration() {
    testFeatureValidation("{ function f() {} }", Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);
    testFeatureValidation(
        "function f() { if (true) { function g() {} } }",
        Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);
    valid("function f() {}");
  }

  public void testFeatureValidation_classes() {
    testFeatureValidation("class C {}", Feature.CLASSES);
    testFeatureValidation("var C = class {}", Feature.CLASSES);
  }

  public void testFeatureValidation_computedProperties() {
    testFeatureValidation("var obj = { ['foo' + 3]: 4};", Feature.COMPUTED_PROPERTIES);
    testFeatureValidation("var { ['foo' + 3]: x} = obj;", Feature.COMPUTED_PROPERTIES);
    testFeatureValidation("class C { ['foobar']() {} }", Feature.COMPUTED_PROPERTIES);
  }

  public void testFeatureValidation_defaultParameters() {
    testFeatureValidation("function f(a = 1) {}", Feature.DEFAULT_PARAMETERS);
    testFeatureValidation("((a = 3) => a)", Feature.DEFAULT_PARAMETERS);
  }

  public void testFeatureValidation_destructuring() {
    testFeatureValidation("var x, {a, b} = obj;", Feature.DESTRUCTURING);
    testFeatureValidation("var x, [a, b] = arr;", Feature.DESTRUCTURING);
    testFeatureValidation("(x = 0, {a, b} = obj);", Feature.DESTRUCTURING);
    testFeatureValidation("x = 0, [a, b] = obj;", Feature.DESTRUCTURING);
    testFeatureValidation("for ({a, b} of c) {}", Feature.DESTRUCTURING);
    testFeatureValidation("for ([a, b] of c) {}", Feature.DESTRUCTURING);
  }

  public void testFeatureValidation_extendedObjectLiterals() {
    testFeatureValidation("var obj = { x };", Feature.EXTENDED_OBJECT_LITERALS);
  }

  public void testFeatureValidation_forOf() {
    testFeatureValidation("for (const a of b) {}", Feature.FOR_OF);
  }

  public void testFeatureValidation_forAwaitOf() {
    testFeatureValidation("for await (const a of b) {}", Feature.FOR_AWAIT_OF);
  }

  public void testFeatureValidation_generatorFunctions() {
    testFeatureValidation("const f = function *() {}", Feature.GENERATORS);
    testFeatureValidation("function *f() {}", Feature.GENERATORS);
    testFeatureValidation("class C { *f() {} }", Feature.GENERATORS);
  }

  public void testFeatureValidation_memberDeclarations() {
    testFeatureValidation("class C { f() {} }", Feature.MEMBER_DECLARATIONS);
    testFeatureValidation("var obj = { f() {} };", Feature.MEMBER_DECLARATIONS);
  }

  public void testFeatureValidation_newTarget() {
    testFeatureValidation("function f() { new.target }", Feature.NEW_TARGET);
  }

  public void testFeatureValidation_restParameters() {
    testFeatureValidation("function f(...rest) {}", Feature.REST_PARAMETERS);
  }

  public void testFeatureValidation_spreadExpressions() {
    testFeatureValidation("f(...arr);", Feature.SPREAD_EXPRESSIONS);
    testFeatureValidation("var arr = [...something];", Feature.SPREAD_EXPRESSIONS);
    testFeatureValidation("var obj = {...something};", Feature.SPREAD_EXPRESSIONS);
  }

  public void testFeatureValidation_super() {
    testFeatureValidation("class C extends B { constructor() { super(); } }", Feature.SUPER);
    testFeatureValidation("class C extends B { f() { super.method(); } }", Feature.SUPER);
  }

  public void testFeatureValidation_templateLiterals() {
    testFeatureValidation("`foo ${3} bar `", Feature.TEMPLATE_LITERALS);
    testFeatureValidation("tag`foo ${3} bar`", Feature.TEMPLATE_LITERALS);
  }

  public void testFeatureValidation_modules() {
    testFeatureValidation("export {x};", Feature.MODULES);
    testFeatureValidation("import {x} from './foo.js';", Feature.MODULES);
  }

  public void testFeatureValidation_exponentOp() {
    testFeatureValidation("2 ** 3", Feature.EXPONENT_OP);
    testFeatureValidation("x **= 3;", Feature.EXPONENT_OP);
  }

  public void testFeatureValidation_asyncFunctions() {
    testFeatureValidation("const f = async function() {}", Feature.ASYNC_FUNCTIONS);
    testFeatureValidation("async function f() {}", Feature.ASYNC_FUNCTIONS);
    testFeatureValidation("class C { async f() {} }", Feature.ASYNC_FUNCTIONS);
    testFeatureValidation("(async () => {})", Feature.ASYNC_FUNCTIONS);
  }

  public void testFeatureValidation_asyncGeneratorFunctions() {
    testFeatureValidation("const f = async function *() {}", Feature.ASYNC_GENERATORS);
    testFeatureValidation("async function *f() {}", Feature.ASYNC_GENERATORS);
    testFeatureValidation("class C { async *f() {} }", Feature.ASYNC_GENERATORS);
  }

  public void testFeatureValidation_objectLiteralsWithSpread() {
    testFeatureValidation("var obj = {...something};", Feature.OBJECT_LITERALS_WITH_SPREAD);
  }

  public void testValidFeatureInScript() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);

    Node n = new Node(Token.SCRIPT);
    n.setInputId(new InputId("something_input"));
    n.setStaticSourceFile(new SimpleSourceFile("something", false));
    expectValid(n, Check.SCRIPT);

    n.addChildToFront(IR.let(IR.name("a"), IR.number(3)));
    expectInvalid(n, Check.SCRIPT);

    n.putProp(Node.FEATURE_SET, FeatureSet.BARE_MINIMUM.with(Feature.LET_DECLARATIONS));
    expectValid(n, Check.SCRIPT);
  }

  private void valid(String code) {
    testSame(code);
    assertTrue(lastCheckWasValid);
  }

  private enum Check {
    SCRIPT,
    STATEMENT,
    EXPRESSION
  }

  private boolean doCheck(Node n, Check level) {
    AstValidator validator = createValidator(createCompiler());
    switch (level) {
      case SCRIPT:
        validator.validateScript(n);
        break;
      case STATEMENT:
        validator.validateStatement(n);
        break;
      case EXPRESSION:
        validator.validateExpression(n);
        break;
    }
    return lastCheckWasValid;
  }

  private void expectInvalid(Node n, Check level) {
    assertFalse(doCheck(n, level));
  }

  private void expectValid(Node n, Check level) {
    assertTrue(doCheck(n, level));
  }

  /**
   * Tests that AstValidator checks for the given feature in the AST
   *
   * <p>This will raise an error if a) the AST parsed from {@code code} lacks {@code feature}, or
   * b) AstValidator does not validate {@code feature}'s presence in the AST.
   */
  private void testFeatureValidation(String code, Feature feature) {
    Node script = parseScriptWithoutCheckingLanguageLevel(code);
    expectValid(script, Check.SCRIPT);

    // Remove `feature` from the SCRIPT node's feature set, checking that it was originally present,
    // and then validate that AstValidator errors because it expects `feature` to be present.
    FeatureSet currentFeatures = NodeUtil.getFeatureSetOfScript(script);
    assertTrue(currentFeatures.contains(feature));

    script.putProp(Node.FEATURE_SET, currentFeatures.without(feature));
    expectInvalid(script, Check.SCRIPT);
  }

  private Node parseScriptWithoutCheckingLanguageLevel(String code) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    Node n = parseExpectedJs(code);
    Node script = n.getFirstChild();
    assertNode(script).hasType(Token.SCRIPT);
    script.setInputId(new InputId("something_input"));
    script.setStaticSourceFile(new SimpleSourceFile("something", false));
    return script;
  }
}
