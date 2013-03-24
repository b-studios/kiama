/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2010-2013 Dominic R B Verity, Macquarie University.
 * Copyright (C) 2011-2013 Anthony M Sloane, Macquarie University.
 *
 * Kiama is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Kiama is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Kiama.  (See files COPYING and COPYING.LESSER.)  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.kiama
package example.iswim.secd

/**
 * Bytecode and value types for the basic SECD machine
 */

// TODO fix reporting of positions of exceptions which come from
// code in PrimValues. Also fix the way their bytecode numbers are
// reported.


import scala.util.parsing.input.Positional
import scala.util.parsing.input.NoPosition
import scala.util.parsing.input.Position
import scala.collection.mutable.ListBuffer
import org.kiama.attribution.Attributable
import org.kiama.machine.Machine
import org.kiama.util.StdoutEmitter

object SECDBase {

    import org.kiama.attribution.Attribution._
    import org.kiama.example.iswim.driver.PrettyPrinter._
    import scala.util.parsing.input.Positional

    /**
     * Base class for SECD bytecode instructions
     */
    private var currPos : Position = NoPosition
    def positionBlock[T] (newPos : Position) (code : => T) : T = {
        val oldPos = currPos
        currPos = newPos
        val res = code
        currPos = oldPos
        res
    }

    abstract class ByteCodeBase extends PrettyPrintable
    abstract class ByteCode extends ByteCodeBase with Attributable with Positional {
        setPos(currPos)
    }
    abstract class Instruction extends ByteCode

    /**
     * Attribute mechanism for automatically numbering bytecode instructions
     */
    val instNumber : ByteCode => Int =
        attr {
            case t if t.isRoot => 1
            case t if t.isFirst => t.parent match {
                case s : Instruction => (s->instNumber) + 1
                case s : ByteCode => s->instNumber
            }
            case t => t.prev[ByteCode]->nextInstNumber
        }

    val nextInstNumber : ByteCode => Int =
        attr {
            case t if t.hasChildren => t.lastChild[ByteCode]->nextInstNumber
            case t => t->instNumber + 1
        }

    /**
     * Type aliases for code sequences and names
     */
    type Name = String
    type Code = List[Instruction]

    /**
     * Code segments (sequences).
     *
     * We use CodeTrees to efficiently combine code together during code generation and then
     * collapse these into a single ListBuffer when they are included into a CodeSegment
     */

    class CodeTree(bdy : List[ByteCodeBase]) extends ByteCodeBase {

        private lazy val flattened : List[Instruction] = {
            val buffer = new ListBuffer[Instruction] ()
            for (b <- bdy) {
                b match {
                    case cs : CodeTree =>
                        buffer ++= cs.flattened
                    case CodeSegment(code) =>
                        buffer ++= code
                    case i : Instruction =>
                        buffer += i
                }
            }
            buffer.toList
        }

        def toCodeSegment : CodeSegment =
            new CodeSegment(flattened)

    }

    object CodeTree {
        def apply(bs : ByteCodeBase*) : CodeTree =
            new CodeTree(bs.toList)
        def apply(bs : List[ByteCodeBase]) : CodeTree =
            new CodeTree(bs)
    }

    case class CodeSegment(code : Code) extends ByteCode {
        override def toDoc : Doc =
            list (code, "CodeSegment",
                  (e : Instruction) =>
                       (e->instNumber).toString <>
                            ':' <+> e.toDoc)

        override def toString : String = code.mkString("CodeSegment(",",",")")
    }

    object CodeSegment {
        def apply(bs : ByteCodeBase*) : CodeSegment =
            new CodeTree(bs.toList).toCodeSegment
    }

    @inline
    implicit def instToCodeTree (inst : Instruction) : CodeTree = CodeTree(inst)

    @inline
    implicit def toCodeSegment (bcb : ByteCodeBase) : CodeSegment =
        bcb match {
            case cs : CodeSegment => cs
            case _ => CodeSegment(bcb)
        }

    /**
     * Function closures and calls
     */
    case class FunctionSpec (
         fn : Option[Name],      // name of function
         pn : Name,              // name of function parameter
         bdy : CodeSegment       // bytecode of body
    ) extends ByteCode {
        override def toDoc : Doc = {
            val name : Doc = fn match {
                                 case Some(n) => n
                                 case None    => "** unnamed **"
                             }
            "FunctionSpec" <>
                parens(nest(line <> name <> ',' <>
                            line <> pn <> ',' <>
                            line <> bdy.toDoc))
        }
    }

    case class MkClosures(fss : List[FunctionSpec]) extends Instruction {
        override def toDoc : Doc =
            list(fss, "MkClosures", (fs : FunctionSpec) => fs.toDoc)
    }

    case class App() extends Instruction
    case class TailApp() extends Instruction

    case class Enter(nms : List[Name]) extends Instruction
    case class BindPrims(nms : List[Name]) extends Instruction
    case class Exit() extends Instruction

    /**
     * Lookup named variable in the environment
     */
    case class Lookup(nm : Name) extends Instruction

    /**
     * Continuation handling
     *
     * We use AppCC rather than the more usual CallCC to avoid
     * name clashes with the corresponding clause of the ISWIM
     * abstract syntax.
     */
    case class AppCC() extends Instruction
    case class Resume() extends Instruction
    case class ResumeFromDump() extends Instruction

    /**
    * Push empty value / machine exception / type value onto the stack.
    */
    case class PushEmpty() extends Instruction
    case class PushMachineException(me : MachineExceptionValue) extends Instruction
    case class PushType(ty : TypeValue) extends Instruction

    /**
    * Make a new exception object from a string object and
    * push it on the stack
    */
    case class MkUserException() extends Instruction

    /**
    * Raise the exception on the top of the stack
    */
    case class RaiseException() extends Instruction

    /**
    * Get the type of the value on the top of the stack
    * as an integer.
    */
    case class GetType() extends Instruction

    /**
     * Base class for SECD values.
     */
    abstract class Value {
        def getType : TypeValue
    }

    /**
     * Type values
     */
    abstract class TypeValue extends Value {
        def getType : TypeValue = TypeTypeValue()
    }
    case class EmptyTypeValue() extends TypeValue
    case class TypeTypeValue() extends TypeValue
    case class ClosureTypeValue() extends TypeValue
    case class ContTypeValue() extends TypeValue
    case class ExceptionTypeValue() extends TypeValue
    case class PrimTypeValue() extends TypeValue

    /**
     * Exception types
     */
    abstract class ExceptionValue extends Value with Positional {
        def getType : TypeValue = ExceptionTypeValue()
    }

    abstract class MachineExceptionValue extends ExceptionValue {
        def message : String
        override def toString : String =
            "MachineExceptionValue" ++ ": " ++ message ++ " at " ++
            this.pos.toString
    }

    case class UnboundVariable() extends MachineExceptionValue {
        def message : String = "unbound variable"
    }
    case class StackUnderflow() extends MachineExceptionValue {
        def message : String = "stack underflow"
    }
    case class TypeError() extends MachineExceptionValue {
        def message : String = "type error"
    }
    case class UnexpectedTermination() extends MachineExceptionValue {
        def message : String = "unexpected termination of code sequence"
    }
    case class UnexpectedExit() extends MachineExceptionValue {
        def message : String = "unexpected Exit instruction"
    }
    case class DumpEmpty() extends MachineExceptionValue {
        def message : String = "resume failed since machine dump is empty"
    }
    case class MalformedInstruction() extends MachineExceptionValue {
        def message : String = "malformed instruction"
    }
    case class MatchError() extends MachineExceptionValue {
        def message : String = "match error, value not matched"
    }
    case class NonExistentPrimitive() extends MachineExceptionValue {
        def message : String = "attempt to bind non-existent primitive"
    }
}

/**
 * The basic SECD machine implementation
 *
 * This only implements the bare bones of an operating
 * SECD machine. In particular it does not provide any
 * primitive builtin functions for operating on data values
 * such as strings and integers. You can add these using
 * traits to implement each one in a stackable manner.
 */
abstract class SECDBase
        extends Machine("SECD") with StdoutEmitter {

    import SECDBase._

    /**
     * Base machine value types
     * Null value
     */
    case class EmptyValue() extends Value {
        override def toString : String = "()"
        def getType : TypeValue = EmptyTypeValue()
    }

    /**
     * Continuation values
     */
    trait Continuation
    case class ContValue(
            s : Stack,
            e : Environment,
            c : Code,
            d : Dump
    ) extends Value with Continuation {
        override def hashCode : Int = super.hashCode
        override def equals(that : Any) : Boolean = super.equals(that)
        override def toString : String = "ContValue@" ++ hashCode.toHexString
        def getType : TypeValue = ContTypeValue()
    }

    /**
     * Closure values
     *
     * The environment parameter must be marked as mutable here
     * so that we may use the "tying the knot" technique to
     * implement recursion.
     */
    case class ClosureValue(
            pn : Name,
            bdy : Code,
            var envir : Environment
    ) extends Value {
        override def hashCode : Int = super.hashCode
        override def equals(that : Any) : Boolean = super.equals(that)
        override def toString : String = "ClosureValue@" ++ hashCode.toHexString
        def getType : TypeValue = ClosureTypeValue()
    }

    /**
     * Primitives - values which contain simple code segments.
     * These can be preloaded into the environment on startup and
     * executed from the stack by executing an App() bytecode.
     */
    case class PrimValue(bdy : Code) extends Value {
        override def hashCode : Int = super.hashCode
        override def equals(that : Any) : Boolean = super.equals(that)
        override def toString : String = "PrimValue@" ++ hashCode.toHexString
        def getType : TypeValue = PrimTypeValue()
    }

    /**
     * Type alias for environments, stacks and so forth
     */
    type Environment = Map[Name,Value]
    type Stack = List[Value]
    type Dump = Continuation

    case class EmptyCont() extends Continuation {
        override def toString : String = "** empty **"
    }

    /**
     * Table of builtin primitives for this machine.
     * These named values may be loaded into the environment
     * using the BindPrims(nms) bytecode.
     */

    def primTable : Map[Name,Value]

    /**
     * Machine registers
     *
     * A little work is needed here to ensure that these
     * registers are printed in a reasonable way when we
     * turn on debugging.
     */

    lazy val stack = new State[Stack]("stack") {
        def toDoc : Doc =
            if (value.isEmpty)
                "** empty **"
            else
                list(value, "Stack")
        override def toString : String =
            pretty(toDoc)
    }

    lazy val envir = new State[Environment]("environment") {
        def toDoc : Doc =
            if (value.isEmpty)
                "** empty **"
            else
                list(value.toList, "Environment",
                     (kv : (Name,Value)) =>
                         kv match {
                             case (nm, v) => {
                                 nm <+> '=' <+> v.toDoc
                             }
                         })
        override def toString : String =
            pretty(toDoc)
    }

    lazy val control = new State[Code]("control") {
        def toDoc : Doc = {
            value match {
                case i :: _ =>
                    "instruction " <> (i->instNumber).toString
                case Nil =>
                    "** empty **"
            }
        }
        override def toString : String =
            pretty(toDoc)
    }

    lazy val dump = new State[Dump]("dump")

    /**
     * Evaluate a single instruction.
     *
     * We implement this as a partial function, which may be
     * overridden in traits in order to compose on handlers
     * for new bytecode instructions (using orElse composition).
     */
    def evalInst : Code ==> Unit = {
        // Empty control sequence - so either return from a function
        // call or stop execution of the machine.
        case Nil => (dump : Dump) match {
            case ContValue(s,e,c,d) => (stack : Stack) match {
                case List(v) =>
                    stack := v :: s
                    envir := e
                    control := c
                    dump := d
                case _ =>  raiseException(UnexpectedTermination())
            }
            case EmptyCont() =>
                if (stack.length != 1 || envir.nonEmpty ||
                    control.nonEmpty) raiseException(UnexpectedTermination())
        }
        // Lookup the value of a variable in the environment.
        case Lookup(nm) :: next => envir.get(nm) match {
            case Some(v) =>
                stack := v :: stack
                control := next
            case None => raiseException(UnboundVariable())
        }
        // Make a mutually recursive group of closures.
        case MkClosures(fss) :: next => {
            var newEnvir : Environment = envir
            var newClos : List[ClosureValue] = Nil
            for {
                FunctionSpec(fn,pn,CodeSegment(bdy)) <- fss
                clos = ClosureValue(pn,bdy,envir)
            } {
                fn match {
                    case Some(n) => newEnvir = newEnvir + (n -> clos)
                    case None => ()
                }
                newClos = clos :: newClos
            }
            for(clos <- newClos) {
                clos.envir = newEnvir
            }
            stack := newClos ++ stack
            control := next
        }
        // Closure application and tail call
        case App() :: next => (stack : Stack) match {
            case ClosureValue(p,b,e) :: rest => rest match {
                case v :: tail =>
                    dump := ContValue(tail,envir,next,dump)
                    stack := Nil
                    envir := e + (p -> v)
                    control := b
                case _ => raiseException(StackUnderflow())
            }
            case PrimValue(b) :: tail =>
                stack := tail
                control := b ++ next
            case _ :: _ => raiseException(TypeError())
            case _ => raiseException(StackUnderflow())
        }
        case TailApp() :: next => (stack : Stack) match {
            case ClosureValue(p,b,e) :: rest => rest match {
                case v :: tail =>
                    stack := Nil
                    envir := e + (p -> v)
                    control := b
                case _ => raiseException(StackUnderflow())
            }
            case PrimValue(b) :: tail =>
                stack := tail
                control := b ++ next
            case _ :: _ => raiseException(TypeError())
            case _ => raiseException(StackUnderflow())
        }
        // Enter and exit binding instructions
        case Enter(nms) :: next =>
            if (stack.length < nms.length)
                raiseException(StackUnderflow())
            else {
                dump := ContValue(stack.drop(nms.length),envir,Nil,dump)
                stack := Nil
                envir := envir ++ (nms.reverse zip stack.take(nms.length))
                control := next
            }
        case Exit() :: next => (dump : Dump) match {
            case ContValue(s,e,Nil,d) => (stack : Stack) match {
                case List(v) =>
                    stack := v :: s
                    envir := e
                    control := next
                    dump := d
                case _ => raiseException(UnexpectedExit())
            }
            case _ => raiseException(UnexpectedExit())
        }
        // Binding instruction for primitives
        case BindPrims(nms) :: next =>
            if (nms.forall({ nm : String => primTable.contains(nm) })) {
                dump := ContValue(stack,envir,Nil,dump)
                stack := Nil
                envir := (nms :\ (envir : Environment))
                            { case (nm,e) => e + (nm->primTable(nm)) }
                control := next
            } else raiseException(NonExistentPrimitive())
        // Continuation handling
        case AppCC() :: next => (stack : Stack) match {
            case ClosureValue(p,b,e) :: tail =>
                val cc = ContValue(tail,envir,next,dump)
                dump := cc
                stack := Nil
                envir := e + (p -> cc)
                control := b
            case _ :: _ => raiseException(TypeError())
            case _ => raiseException(StackUnderflow())
        }
        case Resume() :: next => (stack : Stack) match {
            case ContValue(s,e,c,d) :: v :: tail =>
                dump := d
                stack := v :: s
                envir := e
                control := c
            case _ :: _ :: _ => raiseException(TypeError())
            case _ => raiseException(StackUnderflow())
        }
        case ResumeFromDump() :: next => (dump : Dump) match {
            case ContValue(s,e,c,d) => (stack : Stack) match {
                case v :: tail =>
                    dump := d
                    stack := v :: s
                    envir := e
                    control := c
                case _ => raiseException(StackUnderflow())
            }
            case EmptyCont() => raiseException(DumpEmpty())
        }
        // Push an empty (null) value on the stack.
        case PushEmpty() :: next =>
            stack := EmptyValue() :: stack
            control := next
        // Push a machine exception value on the stack.
        case PushMachineException(me : MachineExceptionValue) :: next =>
            stack := me :: stack
            control := next
        // Push a type value on the stack.
        case PushType(ty : TypeValue) :: next =>
            stack := ty :: stack
            control := next
        // Raise the exception on the top of the stack
        case RaiseException() :: next => (stack : Stack) match {
            case (ex : ExceptionValue) :: _ => raiseException(ex)
            case _ :: _ => raiseException(TypeError())
            case _ => raiseException(StackUnderflow())
        }
        // Get the type of the value on the top of the stack.
        case GetType() :: next => (stack : Stack) match {
            case v :: tail =>
                stack := v.getType :: tail
                control := next
            case _ => raiseException(StackUnderflow())
        }
    }

    /**
     * Rule to execute one step of this machine.
     */
    protected var execSrcPos : Position = NoPosition
    def main () {
        (control : Code) match {
            case inst :: _ if (inst.pos != NoPosition) => execSrcPos = inst.pos
            case _ =>
        }
        evalInst (control : Code)
    }

    /**
     * Raise a machine exception
     *
     * This particular error handler assumes that the environment
     * contains a binding of the name '@exnHandler' to a reference value
     * containing a continuation. This continuation is resumed with
     * the exception value passed to this function as its parameter.
     *
     * If '@exnHandler' is unbound in the environment or is bound to a
     * value that is not a refernce to a continuation then this function
     * reports a machine panic and stops the machine.
     */
    def raiseException (ex : ExceptionValue) {
        ex setPos execSrcPos
        envir.get("@exnHandler") match {
            case Some(ContValue(s,e,c,d)) =>
                dump := d
                stack := ex :: s
                envir := e
                control := c
            case _ => sys.error("Machine Panic: invalid or non-existent exception handler.")
        }
    }
}