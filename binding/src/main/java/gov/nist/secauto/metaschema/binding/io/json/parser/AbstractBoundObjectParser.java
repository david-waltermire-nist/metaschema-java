package gov.nist.secauto.metaschema.binding.io.json.parser;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import gov.nist.secauto.metaschema.binding.BindingException;
import gov.nist.secauto.metaschema.binding.io.json.PropertyValueHandler;
import gov.nist.secauto.metaschema.binding.model.ClassBinding;
import gov.nist.secauto.metaschema.binding.model.property.PropertyAccessor;
import gov.nist.secauto.metaschema.binding.model.property.PropertyBinding;

public abstract class AbstractBoundObjectParser<CLASS, CLASS_BINDING extends ClassBinding<CLASS>> implements BoundObjectParser<CLASS, CLASS_BINDING> {
	private final CLASS_BINDING classBinding;
	private final JsonParsingContext parsingContext;
	private final UnknownPropertyHandler unknownPropertyHandler;

	public AbstractBoundObjectParser(CLASS_BINDING classBinding, JsonParsingContext parsingContext,
			UnknownPropertyHandler unknownPropertyHandler) {
		Objects.requireNonNull(classBinding, "classBinding");
		Objects.requireNonNull(parsingContext, "parsingContext");
		Objects.requireNonNull(unknownPropertyHandler, "unknownPropertyHandler");

		this.classBinding = classBinding;
		this.parsingContext = parsingContext;
		this.unknownPropertyHandler = unknownPropertyHandler;
	}

	protected abstract PropertyBindingSupplier getPropertyBindingSupplier();

	protected abstract PropertyAccessorSupplier getPropertyAccessorSupplier();

	protected PropertyParser getPropertyParser() {
		return (binding) -> false;
	}

	protected CLASS_BINDING getClassBinding() {
		return classBinding;
	}

	@Override
	public JsonParsingContext getParsingContext() {
		return parsingContext;
	}

	protected UnknownPropertyHandler getUnknownPropertyHandler() {
		return unknownPropertyHandler;
	}

	protected void parseProperties(Map<String, PropertyBinding> propertyBindings) throws IOException, BindingException {
		JsonParsingContext parsingContext = getParsingContext();
		JsonParser parser = parsingContext.getEventReader();
		JsonProblemHandler problemHandler = parsingContext.getProblemHandler();
		CLASS_BINDING classBinding = getClassBinding();

		PropertyBindingSupplier propertyBindingSupplier = getPropertyBindingSupplier();
		PropertyAccessorSupplier propertyAccessorSupplier = getPropertyAccessorSupplier();
		PropertyParser propertyParser = getPropertyParser();

		// we are at a start object
		JsonUtil.expectCurrentToken(parser, JsonToken.START_OBJECT);
		// parse to the first field
		parser.nextToken();

		Set<String> parsedProperties = new HashSet<>();
		Set<String> unknownProperties = new HashSet<>();
		String nextFieldName;
		while (JsonToken.FIELD_NAME.equals(parser.currentToken())
				&& (nextFieldName = parser.getCurrentName()) != null) {

//			JsonUtil.expectCurrentToken(parser, JsonToken.FIELD_NAME);
			// parse to "value" token, which may be a START_ARRAY, START_OBJECT, or one of
			// the "VALUE" tokens
			parser.nextToken();

			PropertyBinding propertyBinding = propertyBindings.get(nextFieldName);
			if (propertyBinding == null) {
				// the field is not bound

				// Give the problem handler a chance to handle the property
				if (problemHandler.canHandleUnknownProperty(classBinding, nextFieldName, parsingContext)) {
					for (Map.Entry<PropertyAccessor, Supplier<?>> entry : problemHandler
							.handleUnknownProperty(classBinding, nextFieldName, parsingContext).entrySet()) {
						propertyAccessorSupplier.apply(entry.getKey(), entry.getValue());
					}
				} else {
					// handle it internally if possible (e.g., valueKey with flag)
					Map<PropertyBinding, Supplier<?>> result = getUnknownPropertyHandler().handle(nextFieldName,
							Collections.unmodifiableSet(unknownProperties), parsingContext);
					for (Map.Entry<PropertyBinding, Supplier<?>> entry : result.entrySet()) {
						propertyBindingSupplier.apply(entry.getKey(), entry.getValue());
					}

					unknownProperties.add(nextFieldName);
				}
			} else {
//				logger.info("  Parsing field '{}'", nextFieldName);

				// if the callback doesn't opt to parse the property, parse it here
				if (!propertyParser.parse(propertyBinding)) {
					Supplier<? extends Object> supplier = parseProperty(propertyBinding, parsingContext);
					propertyBindingSupplier.apply(propertyBinding, supplier);
				}
				parsedProperties.add(nextFieldName);
			}
		}

		// Parse to after the end object
		JsonUtil.expectCurrentToken(parser, JsonToken.END_OBJECT);
		parser.nextToken();

		Map<String, PropertyBinding> missingPropertyBindings = new HashMap<>(propertyBindings);
		Set<String> keySet = missingPropertyBindings.keySet();
		keySet.removeAll(parsedProperties);
		for (Map.Entry<PropertyBinding, Supplier<?>> entry : problemHandler
				.handleMissingFields(classBinding, missingPropertyBindings, parsingContext).entrySet()) {
			propertyBindingSupplier.apply(entry.getKey(), entry.getValue());
		}

//		for (String key : keySet) {
//			logger.info("  Unparsed field '{}'", key);
//		}
	}

	/**
	 * Parses the field and value for the bound property provided by the
	 * {@code propertyBinding} parameter.
	 * <p>
	 * When called, the parser's current token is expected to be at the VALUE.
	 * <p>
	 * When this method returns, the parser's current token is expected to be at the
	 * next {@link JsonToken#FIELD_NAME} if there are more fields to parse, or at
	 * the {@link JsonToken#END_OBJECT} if the last field has been parsed.
	 * 
	 * @param propertyBinding
	 * @param parsingContext
	 * @return
	 * @throws BindingException
	 * @throws IOException
	 */
	public Supplier<? extends Object> parseProperty(PropertyBinding propertyBinding, JsonParsingContext parsingContext)
			throws BindingException, IOException {

		PropertyValueHandler propertyValueHandler = PropertyValueHandler.newPropertyValueHandler(getClassBinding(),
				propertyBinding, parsingContext);

		while (propertyValueHandler.parseNextFieldValue(parsingContext)) {
			// after calling parseNextField the current token is expected to be at the next
			// field to parse or at the END_OBJECT for the containing object
		}

		return propertyValueHandler.getObjectSupplier();
	}

	@FunctionalInterface
	public interface PropertyParser {
		boolean parse(PropertyBinding propertyBinding) throws BindingException;
	}

	@FunctionalInterface
	public interface UnknownPropertyHandler {
		Map<PropertyBinding, Supplier<?>> handle(String fieldName, Set<String> parsedUnknownProperties,
				JsonParsingContext parsingContext) throws BindingException;
	}

	@FunctionalInterface
	public interface PropertyBindingSupplier {
		void apply(PropertyBinding propertyBinding, Supplier<?> supplier) throws BindingException;
	}

	@FunctionalInterface
	public interface PropertyAccessorSupplier {
		void apply(PropertyAccessor propertyAccessor, Supplier<?> supplier) throws BindingException;
	}
}
