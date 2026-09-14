package com.aizerohub.ragguard.junit5;

import com.aizerohub.ragguard.core.engine.EvaluationEngine;
import com.aizerohub.ragguard.core.judge.EmbeddingModel;
import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.RagChain;
import com.aizerohub.ragguard.core.testset.TestSetFormatException;
import com.aizerohub.ragguard.core.testset.TestSetLoader;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Evaluates the test set once per {@link RagTest @RagTest} class and injects
 * the {@link EvaluationReport} into test method parameters.
 */
public final class RagGuardExtension implements BeforeAllCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(RagGuardExtension.class);
    private static final String REPORT_KEY = "evaluationReport";

    @Override
    public void beforeAll(ExtensionContext context) {
        // Eagerly compute so supplier misconfiguration fails loudly, once.
        reportFor(context);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == EvaluationReport.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        if (!supportsParameter(parameterContext, extensionContext)) {
            throw new ParameterResolutionException(
                    "only EvaluationReport parameters are supported by RagGuardExtension");
        }
        return reportFor(extensionContext);
    }

    private static EvaluationReport reportFor(ExtensionContext context) {
        ExtensionContext root = context.getRoot();
        return root.getStore(NAMESPACE)
                .getOrComputeIfAbsent(REPORT_KEY + ':' + context.getRequiredTestClass().getName(),
                        key -> evaluateTestClass(context.getRequiredTestClass()),
                        EvaluationReport.class);
    }

    private static EvaluationReport evaluateTestClass(Class<?> testClass) {
        RagTest configuration = testClass.getAnnotation(RagTest.class);
        if (configuration == null) {
            throw new IllegalStateException(testClass.getName() + " is not annotated with @RagTest");
        }
        RagChain chain = invokeSupplier(testClass, RagChainSupplier.class, RagChain.class, "RagChain");
        Judge judge = invokeSupplier(testClass, RagJudgeSupplier.class, Judge.class, "Judge");
        EmbeddingModel embedding = invokeSupplier(testClass, RagEmbeddingSupplier.class,
                EmbeddingModel.class, "EmbeddingModel");
        EvaluationEngine engine = EvaluationEngine.builder()
                .chain(chain)
                .judge(judge)
                .embedding(embedding)
                .build();
        return engine.run(loadTestSet(configuration.testSet(), testClass));
    }

    private static <T> T invokeSupplier(Class<?> testClass, Class<? extends java.lang.annotation.Annotation> marker,
                                        Class<T> expectedType, String description) {
        Method method = findSupplierMethod(testClass, marker);
        if (method == null) {
            throw new IllegalStateException("@" + marker.getSimpleName() + " static method returning "
                    + description + " not found on " + testClass.getName()
                    + " — it is required by @RagTest");
        }
        Object value;
        try {
            method.setAccessible(true);
            value = method.invoke(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot invoke " + method, e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("supplier method " + method.getName() + " failed: "
                    + e.getCause().getMessage(), e.getCause());
        }
        if (value == null) {
            throw new IllegalStateException("supplier method " + method.getName() + " returned null");
        }
        if (!expectedType.isInstance(value)) {
            throw new IllegalStateException("supplier method " + method.getName() + " must return "
                    + expectedType.getName() + " but returned " + value.getClass().getName());
        }
        return expectedType.cast(value);
    }

    private static Method findSupplierMethod(Class<?> testClass,
                                             Class<? extends java.lang.annotation.Annotation> marker) {
        for (Class<?> c = testClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.isAnnotationPresent(marker)
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0) {
                    return method;
                }
            }
        }
        return null;
    }

    private static List<com.aizerohub.ragguard.core.model.RagTestCase> loadTestSet(String location,
                                                                                   Class<?> testClass) {
        String trimmed = location == null || location.isBlank()
                ? "ragguard-test-set.yml"
                : location.trim();
        String classpathResource = trimmed.startsWith("classpath:")
                ? trimmed.substring("classpath:".length())
                : trimmed;
        // 1. file system, 2. classpath
        Path file = Path.of(classpathResource);
        if (Files.isRegularFile(file)) {
            return TestSetLoader.loadYaml(file);
        }
        try (InputStream in = testClass.getResourceAsStream("/" + stripLeadingSlash(classpathResource))) {
            if (in == null) {
                throw new TestSetFormatException("test set not found: '" + trimmed
                        + "' (not a file and not on the classpath)");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return TestSetLoader.loadYaml(reader);
            }
        } catch (IOException e) {
            throw new TestSetFormatException("cannot read test set: " + trimmed, e);
        }
    }

    private static String stripLeadingSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
