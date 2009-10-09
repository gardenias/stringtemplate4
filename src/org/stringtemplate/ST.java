/*
 [The "BSD licence"]
 Copyright (c) 2009 Terence Parr
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.stringtemplate;

import org.stringtemplate.interp.*;
import org.stringtemplate.interp.Compiler;

import java.util.*;
import java.io.StringWriter;

public class ST {
    public String name = ANON_NAME;
    
    /** The code to interpret; it pulls from attributes and this template's
     *  group of templates to evaluate to string.
     */
    public CompiledST code;

    /** The group that was asked to create this instance.  This is
     *  fixed once we call toString() on an ST.  I wish we could leave
     *  this field out but people won't want to specify group to toString().
     */
    public STGroup group = STGroup.defaultGroup;
    
    /** Map an attribute name to its value(s). */
    Map<String,Object> attributes;

    /** Enclosing instance if I'm embedded within another template.
     *  IF-subtemplates are considered embedded as well.
     */
    ST enclosingInstance; // who's your daddy?
    public static final String ANON_NAME = "anonymous";

    /** Just an alias for ArrayList, but this way I can track whether a
     *  list is something ST created or it's an incoming list.
     */
    public static final class AttributeList extends ArrayList {
        public AttributeList(int size) { super(size); }
        public AttributeList() { super(); }
    }
    
    public ST() {;}
    
    public ST(String template) throws Exception {
        this.code = new Compiler().compile(template);
    }

    public void add(String name, Object value) {
        if ( value==null || name==null ) return;
        if ( name.indexOf('.')>=0 ) {
            throw new IllegalArgumentException("cannot have '.' in attribute names");
        }

        if ( value instanceof ST ) ((ST)value).enclosingInstance = this;

        Object curvalue = null;
        if ( attributes!=null ) curvalue = attributes.get(name);
        if ( curvalue==null ) { // new attribute
            setAttribute(name, value);
            return;
        }

        // attribute will be multi-valued for sure now
        // convert current attribute to list if not already
        // copy-on-write semantics; copy a list injected by user to add new value
        AttributeList multi = convertToAttributeList(curvalue);
        setAttribute(name, multi); // replace with list

        // now, add incoming value to multi-valued attribute
        if ( value instanceof List ) {
            // flatten incoming list into existing list
            multi.addAll((List)value);
        }
        else if ( value.getClass().isArray() ) {
            multi.addAll(Arrays.asList((Object[])value));
        }
        else {
            multi.add(value);
        }
    }

    public void setAttribute(String name, Object value) {
        if ( attributes==null ) attributes = new HashMap<String,Object>();
        attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        if ( attributes==null ) return null;
        return attributes.get(name);
    }

    protected AttributeList convertToAttributeList(Object curvalue) {
        AttributeList multi;
        if ( curvalue.getClass() == AttributeList.class ) { // already a list made by ST
            multi = (AttributeList)curvalue;
        }
        else if ( curvalue instanceof List) { // existing attribute is non-ST List
            // must copy to an ST-managed list before adding new attribute
            // (can't alter incoming attributes)
            List listAttr = (List)curvalue;
            multi = new AttributeList(listAttr.size());
            multi.addAll(listAttr);
        }
        else if ( curvalue.getClass().isArray() ) { // copy array to list
            Object[] a = (Object[])curvalue;
            multi = new AttributeList(a.length);
            multi.addAll(Arrays.asList(a)); // asList doesn't copy as far as I can tell
        }
        else {
            // curvalue nonlist and we want to add an attribute
            // must convert curvalue existing to list
            multi = new AttributeList(); // make list to hold multiple values
            multi.add(curvalue);         // add previous single-valued attribute
        }
        return multi;
    }

    /** If an instance of x is enclosed in a y which is in a z, return
     *  a String of these instance names in order from topmost to lowest;
     *  here that would be "[z y x]".
     */
    public String getEnclosingInstanceStackString() {
        List<String> names = new LinkedList<String>();
        ST p = this;
        while ( p!=null ) {
            String name = p.name;
            names.add(0,name);
            p = p.enclosingInstance;
        }
        return names.toString().replaceAll(",","");
    }    

    public String toString() {
        return "<<"+name+">>";
    }

    public String render() {
        Interpreter interp = new Interpreter();
        interp.group = this.group;
        interp.out = new StringWriter();
        interp.exec(this);
        return interp.out.toString();
    }
}
