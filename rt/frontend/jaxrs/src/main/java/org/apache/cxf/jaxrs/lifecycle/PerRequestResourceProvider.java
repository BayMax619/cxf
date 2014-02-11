/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.jaxrs.lifecycle;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.utils.ExceptionUtils;
import org.apache.cxf.jaxrs.utils.InjectionUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.jaxrs.utils.ResourceUtils;
import org.apache.cxf.message.Message;

/**
 * The default per-request resource provider which creates 
 * a new resource instance per every request
 */
public class PerRequestResourceProvider implements ResourceProvider {
    private Constructor<?> c;
    private Method postConstructMethod;
    private Method preDestroyMethod;
   
    public PerRequestResourceProvider(Class<?> clazz) {
        c = ResourceUtils.findResourceConstructor(clazz, true);
        if (c == null) {
            throw new RuntimeException("Resource class " + clazz
                                       + " has no valid constructor");
        }
        postConstructMethod = ResourceUtils.findPostConstructMethod(clazz);
        preDestroyMethod = ResourceUtils.findPreDestroyMethod(clazz);
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean isSingleton() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public Object getInstance(Message m) {  
        return createInstance(m);
    }
    
    protected Object createInstance(Message m) {
        
        Object[] values = ResourceUtils.createConstructorArguments(c, m);
        try {
            Object instance = values.length > 0 ? c.newInstance(values) : c.newInstance(new Object[]{});
            InjectionUtils.invokeLifeCycleMethod(instance, postConstructMethod);
            return instance;
        } catch (InstantiationException ex) {
            String msg = "Resource class " + c.getDeclaringClass().getName() + " can not be instantiated";
            throw ExceptionUtils.toInternalServerErrorException(null, serverError(msg));
        } catch (IllegalAccessException ex) {
            String msg = "Resource class " + c.getDeclaringClass().getName() + " can not be instantiated"
                + " due to IllegalAccessException";
            throw ExceptionUtils.toInternalServerErrorException(null, serverError(msg));
        } catch (InvocationTargetException ex) {
            Response r = JAXRSUtils.convertFaultToResponse(ex.getCause(), m);
            if (r != null) {
                m.getExchange().put(Response.class, r);
                throw new WebApplicationException();
            }
            String msg = "Resource class "
                + c.getDeclaringClass().getName() + " can not be instantiated"
                + " due to InvocationTargetException";
            throw ExceptionUtils.toInternalServerErrorException(null, serverError(msg));
        }
        
    }

    private Response serverError(String msg) {
        return Response.serverError().entity(msg).build();
    }
    
    /**
     * {@inheritDoc}
     */
    public void releaseInstance(Message m, Object o) {
        InjectionUtils.invokeLifeCycleMethod(o, preDestroyMethod);
    }

    /**
     * {@inheritDoc}
     */
    public Class<?> getResourceClass() {
        return c.getDeclaringClass();
    }
    
    
}
