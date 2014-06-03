/**
 * Copyright (c) 2012, 2014, Credit Suisse (Anatole Tresch), Werner Keil and others by the @author tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.javamoney.moneta;

import org.javamoney.moneta.internal.MoneyAmountFactory;
import org.javamoney.moneta.spi.AbstractMoney;
import org.javamoney.moneta.spi.DefaultNumberValue;
import org.javamoney.moneta.spi.MonetaryConfig;

import javax.money.*;

import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default immutable implementation of {@link MonetaryAmount} based
 * on {@link BigDecimal} as numeric representation.
 * <p/>
 * As required by {@link MonetaryAmount} this class is final, thread-safe,
 * immutable and serializable.
 * <p/>
 * This class can be configured with an arbitrary {@link MonetaryContext}. The
 * default {@link MonetaryContext} used models by default the same settings as
 * {@link MathContext#DECIMAL64} . This default {@link MonetaryContext} can also
 * be reconfigured by adding a file {@code /javamoney.properties} to the
 * classpath, with the following content:
 * <p/>
 * <pre>
 * # Default MathContext for Money
 * #-------------------------------
 * # Custom MonetaryContext, overrides default entries from
 * # org.javamoney.moneta.Money.monetaryContext
 * # RoundingMode hereby is optional (default = HALF_EVEN)
 * org.javamoney.moneta.Money.defaults.precision=256
 * org.javamoney.moneta.Money.defaults.roundingMode=HALF_EVEN
 * </pre>
 *
 * @author Anatole Tresch
 * @author Werner Keil
 * @version 0.7
 */
public final class Money extends AbstractMoney implements Comparable<MonetaryAmount>, Serializable{

    /**
     * serialVersionUID.
     */
    private static final long serialVersionUID = -7565813772046251748L;

    /**
     * The default {@link MonetaryContext} applied, if not set explicitly on
     * creation.
     */
    public static final MonetaryContext DEFAULT_MONETARY_CONTEXT = initDefaultMathContext();

    /**
     * The numeric part of this amount.
     */
    private BigDecimal number;

    /**
     * Required for deserialization only.
     */
    private Money(){
    }

    /**
     * Creates a new instance os {@link Money}.
     *
     * @param currency the currency, not null.
     * @param number   the amount, not null.
     * @throws ArithmeticException If the number exceeds the capabilities of the default
     *                             {@link MonetaryContext}.
     */
    private Money(BigDecimal number, CurrencyUnit currency){
        this(number, currency, null);
    }

    /**
     * Evaluates the default {@link MonetaryContext} to be used for
     * {@link Money}. The default {@link MonetaryContext} can be configured by
     * adding a file {@code /javamoney.properties} from the classpath with the
     * following content:
     * <p/>
     * <pre>
     * # Default MathContext for Money
     * #-------------------------------
     * # Custom MathContext, overrides entries from org.javamoney.moneta.Money.mathContext
     * # RoundingMode hereby is optional (default = HALF_EVEN)
     * org.javamoney.moneta.Money.defaults.precision=256
     * org.javamoney.moneta.Money.defaults.roundingMode=HALF_EVEN
     * </pre>
     * <p/>
     * Hereby the roundingMode constants are the same as defined on
     * {@link RoundingMode}.
     *
     * @return default MonetaryContext, never {@code null}.
     */
    private static MonetaryContext initDefaultMathContext(){
        try{
            Map<String,String> config = MonetaryConfig.getConfig();
            String value = config.get("org.javamoney.moneta.Money.defaults.precision");
            if (Objects.nonNull(value)) {
                int prec = Integer.parseInt(value);
                value = config.get("org.javamoney.moneta.Money.defaults.roundingMode");
                RoundingMode rm =
                		Objects.nonNull(value) ? RoundingMode.valueOf(value.toUpperCase(Locale.ENGLISH)) : RoundingMode.HALF_UP;
                MonetaryContext mc =
                        new MonetaryContext.Builder().setPrecision(prec).setObject(rm).setAmountType(Money.class)
                                .build();
                Logger.getLogger(Money.class.getName())
                        .info("Using custom MathContext: precision=" + prec + ", roundingMode=" + rm); // TODO why info?
                return mc;
            }else{
                MonetaryContext.Builder builder = new MonetaryContext.Builder(Money.class);
                value = config.get("org.javamoney.moneta.Money.defaults.mathContext");
                if (Objects.nonNull(value)) {
                    switch(value.toUpperCase(Locale.ENGLISH)){
                        case "DECIMAL32":
                            Logger.getLogger(Money.class.getName())
                                    .info("Using MathContext.DECIMAL32"); // TODO why info?
                            builder.setObject(MathContext.DECIMAL32);
                            break;
                        case "DECIMAL64":
                            Logger.getLogger(Money.class.getName())
                                    .info("Using MathContext.DECIMAL64"); // TODO why info?
                            builder.setObject(MathContext.DECIMAL64);
                            break;
                        case "DECIMAL128":
                            Logger.getLogger(Money.class.getName())
                                    .info("Using MathContext.DECIMAL128"); // TODO why info?
                            builder.setObject(MathContext.DECIMAL128);
                            break;
                        case "UNLIMITED":
                            Logger.getLogger(Money.class.getName())
                                    .info("Using MathContext.UNLIMITED"); // TODO why info?
                            builder.setObject(MathContext.UNLIMITED);
                            break;
                    }
                }else{
                    Logger.getLogger(Money.class.getName())
                            .info("Using default MathContext.DECIMAL64"); // TODO why info?
                    builder.setObject(MathContext.DECIMAL64);
                }
                return builder.build();
            }
        }
        catch(Exception e){
            Logger.getLogger(Money.class.getName())
                    .log(Level.SEVERE, "Error evaluating default NumericContext, using default (NumericContext.NUM64).",
                         e);
            return new MonetaryContext.Builder(Money.class).setObject(MathContext.DECIMAL64).build();
        }
    }

    /**
     * Creates a new instance of {@link Money}.
     *
     * @param currency        the currency, not {@code null}.
     * @param number          the amount, not {@code null}.
     * @param monetaryContext the {@link MonetaryContext}, if {@code null}, the default is
     *                        used.
     * @throws ArithmeticException If the number exceeds the capabilities of the
     *                             {@link MonetaryContext} used.
     */
    private Money(BigDecimal number, CurrencyUnit currency, MonetaryContext monetaryContext){
        super(currency, monetaryContext);
        Objects.requireNonNull(number, "Number is required.");
		if (Objects.nonNull(monetaryContext)) {
			this.number = getBigDecimal(number, monetaryContext);
		} else {
			this.number = getBigDecimal(number, DEFAULT_MONETARY_CONTEXT);
		}
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#getNumber()
     */
    public NumberValue getNumber(){
        return new DefaultNumberValue(number);
    }

    /**
     * Method that returns BigDecimal.ZERO, if {@link #isZero()}, and
     * {@link #number #stripTrailingZeros()} in all other cases.
     *
     * @return the stripped number value.
     */
    public BigDecimal getNumberStripped(){
        if(isZero()){
            return BigDecimal.ZERO;
        }
        return this.number.stripTrailingZeros();
    }

    /*
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(MonetaryAmount o){
        Objects.requireNonNull(o);
        int compare = getCurrency().getCurrencyCode().compareTo(o.getCurrency().getCurrencyCode());
        if(compare == 0){
            compare = this.number.compareTo(Money.from(o).number);
        }
        return compare;
    }

    // Arithmetic Operations

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#abs()
     */
    @Override
    public Money abs(){
        if(this.isPositiveOrZero()){
            return this;
        }
        return negate();
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#divide(javax.money.MonetaryAmount)
     */
    @Override
    public Money divide(long divisor){
        if(divisor == 1L){
            return this;
        }
        return divide(BigDecimal.valueOf(divisor));
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#divide(javax.money.MonetaryAmount)
     */
    @Override
    public Money divide(double divisor){
        if(divisor == 1.0){
            return this;
        }
        return divide(new BigDecimal(String.valueOf(divisor)));
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * javax.money.MonetaryAmount#divideAndRemainder(javax.money.MonetaryAmount)
     */
    @Override
    public Money[] divideAndRemainder(long divisor){
        if(divisor==1L){
            return new Money[]{this, Money.of(0L, getCurrency())};
        }
        return divideAndRemainder(BigDecimal.valueOf(divisor));
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * javax.money.MonetaryAmount#divideAndRemainder(javax.money.MonetaryAmount)
     */
    @Override
    public Money[] divideAndRemainder(double divisor){
        return divideAndRemainder(new BigDecimal(String.valueOf(divisor)));
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#multiply(Number)
     */
    @Override
    public Money multiply(long multiplicand){
        if(multiplicand==1L){
            return this;
        }
        return multiply(BigDecimal.valueOf(multiplicand));
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#multiply(Number)
     */
    @Override
    public Money multiply(double multiplicand){
        if(multiplicand==1.0d){
            return this;
        }
        return multiply(new BigDecimal(String.valueOf(multiplicand)));
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#remainder(Number)
     */
    @Override
    public Money remainder(long divisor){
        if(divisor==1L){
            return this;
        }
        return remainder(BigDecimal.valueOf(divisor));
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#remainder(Number)
     */
    @Override
    public Money remainder(double divisor){
        return remainder(new BigDecimal(String.valueOf(divisor)));
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#isZero()
     */
    @Override
    public boolean isZero(){
        return signum() == 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#isPositive()
     */
    @Override
    public boolean isPositive(){
        return signum() == 1;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#isPositiveOrZero()
     */
    @Override
    public boolean isPositiveOrZero(){
        return signum() >= 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#isNegative()
     */
    @Override
    public boolean isNegative(){
        return signum() == -1;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#isNegativeOrZero()
     */
    @Override
    public boolean isNegativeOrZero(){
        return signum() <= 0;
    }


    /*
     * }(non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#query(javax.money.MonetaryQuery)
     */
    @Override
    public <R> R query(MonetaryQuery<R> query){
        Objects.requireNonNull(query);
        try{
            return query.queryFrom(this);
        }
        catch(MonetaryException e){
            throw e;
        }
        catch(Exception e){
            throw new MonetaryException("Query failed: " + query, e);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#with(javax.money.MonetaryOperator)
     */
    @Override
    public Money with(MonetaryOperator operator){
        Objects.requireNonNull(operator);
        try{
            return Money.class.cast(operator.apply(this));
        }
        catch(MonetaryException e){
            throw e;
        }
        catch(Exception e){
            throw new MonetaryException("Operator failed: " + operator, e);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#add(javax.money.MonetaryAmount)
     */
    @Override
    public Money add(MonetaryAmount amount){
        checkAmountParameter(amount);
        if(amount.isZero()){
            return this;
        }
        return new Money(this.number.add(amount.getNumber().numberValue(BigDecimal.class)), getCurrency());
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#divide(java.lang.Number)
     */
    @Override
    public Money divide(Number divisor){
        BigDecimal divisorBD = getBigDecimal(divisor);
        if(divisorBD.equals(BigDecimal.ONE)){
            return this;
        }
        BigDecimal dec = this.number.divide(divisorBD, getMathContext(getMonetaryContext(), RoundingMode.HALF_EVEN));
        return new Money(dec, getCurrency());
    }

    @Override
    public Money[] divideAndRemainder(Number divisor){
        BigDecimal divisorBD = getBigDecimal(divisor);
        if(divisorBD.equals(BigDecimal.ONE)){
            return new Money[]{this, new Money(BigDecimal.ZERO, getCurrency())};
        }
        BigDecimal[] dec = this.number.divideAndRemainder(divisorBD);
        return new Money[]{new Money(dec[0], getCurrency()), new Money(dec[1], getCurrency())};
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.javamoney.moneta.AbstractMoney#divideToIntegralValue(java.lang.Number
     * )
     */
    @Override
    public Money divideToIntegralValue(long divisor){
        return divideToIntegralValue(getBigDecimal(divisor));
    }

    @Override
    public Money divideToIntegralValue(double divisor){
        return divideToIntegralValue(getBigDecimal(divisor));
    }

    @Override
    public Money divideToIntegralValue(Number divisor){
        BigDecimal divisorBD = getBigDecimal(divisor);
        BigDecimal dec = this.number.divideToIntegralValue(divisorBD);
        return new Money(dec, getCurrency());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.javamoney.moneta.AbstractMoney#multiply(java.lang.Number)
     */
    @Override
    public Money multiply(Number multiplicand){
        BigDecimal multiplicandBD = getBigDecimal(multiplicand);
        if(multiplicandBD.equals(BigDecimal.ONE)){
            return this;
        }
        BigDecimal dec = this.number.multiply(multiplicandBD);
        return new Money(dec, getCurrency());
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#negate()
     */
    @Override
    public Money negate(){
        return new Money(this.number.negate(), getCurrency());
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#plus()
     */
    @Override
    public Money plus(){
        return new Money(this.number.plus(), getCurrency());
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#subtract(javax.money.MonetaryAmount)
     */
    @Override
    public Money subtract(MonetaryAmount subtrahend){
        checkAmountParameter(subtrahend);
        if(subtrahend.isZero()){
            return this;
        }
        return new Money(this.number.subtract(subtrahend.getNumber().numberValue(BigDecimal.class)), getCurrency());
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#stripTrailingZeros()
     */
    @Override
    public Money stripTrailingZeros(){
        if(isZero()){
            return new Money(BigDecimal.ZERO, getCurrency());
        }
        return new Money(this.number.stripTrailingZeros(), getCurrency());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.javamoney.moneta.AbstractMoney#remainder(java.math.BigDecimal)
     */
    @Override
    public Money remainder(Number divisor){
        BigDecimal bd = getBigDecimal(divisor);
        return new Money(this.number.remainder(bd), getCurrency());
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#scaleByPowerOfTen(int)
     */
    @Override
    public Money scaleByPowerOfTen(int n){
        return new Money(this.number.scaleByPowerOfTen(n), getCurrency());
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#signum()
     */
    @Override
    public int signum(){
        return this.number.signum();
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#isLessThan(javax.money.MonetaryAmount)
     */
    @Override
    public boolean isLessThan(MonetaryAmount amount){
        checkAmountParameter(amount);
        return number.stripTrailingZeros()
                .compareTo(amount.getNumber().numberValue(BigDecimal.class).stripTrailingZeros()) < 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * javax.money.MonetaryAmount#isLessThanOrEqualTo(javax.money.MonetaryAmount
     * )
     */
    @Override
    public boolean isLessThanOrEqualTo(MonetaryAmount amount){
        checkAmountParameter(amount);
        return number.stripTrailingZeros()
                .compareTo(amount.getNumber().numberValue(BigDecimal.class).stripTrailingZeros()) <= 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#isGreaterThan(javax.money.MonetaryAmount)
     */
    @Override
    public boolean isGreaterThan(MonetaryAmount amount){
        checkAmountParameter(amount);
        return number.stripTrailingZeros()
                .compareTo(amount.getNumber().numberValue(BigDecimal.class).stripTrailingZeros()) > 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * javax.money.MonetaryAmount#isGreaterThanOrEqualTo(javax.money.MonetaryAmount
     * ) #see
     */
    @Override
    public boolean isGreaterThanOrEqualTo(MonetaryAmount amount){
        checkAmountParameter(amount);
        return number.stripTrailingZeros()
                .compareTo(amount.getNumber().numberValue(BigDecimal.class).stripTrailingZeros()) >= 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#isEqualTo(javax.money.MonetaryAmount)
     */
    @Override
    public boolean isEqualTo(MonetaryAmount amount){
        checkAmountParameter(amount);
        return number.stripTrailingZeros()
                .compareTo(amount.getNumber().numberValue(BigDecimal.class).stripTrailingZeros()) == 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.money.MonetaryAmount#getFactory()
     */
    @Override
    public MonetaryAmountFactory<Money> getFactory(){
        return new MoneyAmountFactory().setAmount(this);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.javamoney.moneta.AbstractMoney#getDefaultMonetaryContext()
     */
    @Override
    protected MonetaryContext getDefaultMonetaryContext(){
        return DEFAULT_MONETARY_CONTEXT;
    }

    /**
     * Implement serialization explicitly.
     */
    private void writeObject(ObjectOutputStream oos) throws IOException{
        oos.writeObject(this.number);
        oos.writeObject(this.currency);
        oos.writeObject(this.monetaryContext);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
            return true;
        }
		if (obj instanceof Money) {
			Money other = (Money) obj;
			return Objects.equals(getCurrency(), other.getCurrency())
					&& Objects.equals(getNumberStripped(),
							other.getNumberStripped());
		}
		return false;
	}

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString(){
        return getCurrency().getCurrencyCode() + ' ' + number.toString();
    }

    /**
     * Implement deserialization explicitly.
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException{
        this.number = (BigDecimal) ois.readObject();
        this.currency = (CurrencyUnit) ois.readObject();
        this.monetaryContext = (MonetaryContext) ois.readObject();
    }

    @SuppressWarnings("unused")
    private void readObjectNoData() throws ObjectStreamException{
        if (Objects.isNull(this.number)) {
            this.number = BigDecimal.ZERO;
        }
        if (Objects.isNull(this.monetaryContext)) {
            this.monetaryContext = DEFAULT_MONETARY_CONTEXT;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode(){
		return Objects.hash(getCurrency(), getNumberStripped());
    }

    /**
     * Creates a new instance of {@link Money}, using the default
     * {@link MonetaryContext}.
     *
     * @param number   numeric value, not {@code null}.
     * @param currency currency unit, not {@code null}.
     * @return a {@code Money} combining the numeric value and currency unit.
     * @throws ArithmeticException If the number exceeds the capabilities of the default
     *                             {@link MonetaryContext} used.
     */
    public static Money of(BigDecimal number, CurrencyUnit currency){
        return new Money(number, currency);
    }

    /**
     * Creates a new instance of {@link Money}, using an explicit
     * {@link MonetaryContext}.
     *
     * @param number          numeric value, not {@code null}.
     * @param currency        currency unit, not {@code null}.
     * @param monetaryContext the {@link MonetaryContext} to be used, if {@code null} the
     *                        default {@link MonetaryContext} is used.
     * @return a {@code Money} instance based on the monetary context with the
     * given numeric value, currency unit.
     * @throws ArithmeticException If the number exceeds the capabilities of the
     *                             {@link MonetaryContext} used.
     */
    public static Money of(BigDecimal number, CurrencyUnit currency, MonetaryContext monetaryContext){
        return new Money(number, currency, monetaryContext);
    }

    /**
     * Creates a new instance of {@link Money}, using the default
     * {@link MonetaryContext}.
     *
     * @param currency The target currency, not null.
     * @param number   The numeric part, not null.
     * @return A new instance of {@link Money}.
     * @throws ArithmeticException If the number exceeds the capabilities of the default
     *                             {@link MonetaryContext} used.
     */
    public static Money of(Number number, CurrencyUnit currency){
        return new Money(getBigDecimal(number), currency);
    }

    /**
     * Creates a new instance of {@link Money}, using an explicit
     * {@link MonetaryContext}.
     *
     * @param currency        The target currency, not null.
     * @param number          The numeric part, not null.
     * @param monetaryContext the {@link MonetaryContext} to be used, if {@code null} the
     *                        default {@link MonetaryContext} is used.
     * @return A new instance of {@link Money}.
     * @throws ArithmeticException If the number exceeds the capabilities of the
     *                             {@link MonetaryContext} used.
     */
    public static Money of(Number number, CurrencyUnit currency, MonetaryContext monetaryContext){
        return new Money(getBigDecimal(number), currency, monetaryContext);
    }

    /**
     * Static factory method for creating a new instance of {@link Money}.
     *
     * @param currencyCode The target currency as ISO currency code.
     * @param number       The numeric part, not null.
     * @return A new instance of {@link Money}.
     */
    public static Money of(Number number, String currencyCode){
        return new Money(getBigDecimal(number), MonetaryCurrencies.getCurrency(currencyCode));
    }

    /**
     * Static factory method for creating a new instance of {@link Money}.
     *
     * @param currencyCode The target currency as ISO currency code.
     * @param number       The numeric part, not null.
     * @return A new instance of {@link Money}.
     */
    public static Money of(BigDecimal number, String currencyCode){
        return new Money(number, MonetaryCurrencies.getCurrency(currencyCode));
    }

    /**
     * Static factory method for creating a new instance of {@link Money}.
     *
     * @param currencyCode    The target currency as ISO currency code.
     * @param number          The numeric part, not null.
     * @param monetaryContext the {@link MonetaryContext} to be used, if {@code null} the
     *                        default {@link MonetaryContext} is used.
     * @return A new instance of {@link Money}.
     */
    public static Money of(Number number, String currencyCode, MonetaryContext monetaryContext){
        return new Money(getBigDecimal(number), MonetaryCurrencies.getCurrency(currencyCode), monetaryContext);
    }

    /**
     * Static factory method for creating a new instance of {@link Money}.
     *
     * @param currencyCode    The target currency as ISO currency code.
     * @param number          The numeric part, not null.
     * @param monetaryContext the {@link MonetaryContext} to be used, if {@code null} the
     *                        default {@link MonetaryContext} is used.
     * @return A new instance of {@link Money}.
     */
    public static Money of(BigDecimal number, String currencyCode, MonetaryContext monetaryContext){
        return new Money(number, MonetaryCurrencies.getCurrency(currencyCode), monetaryContext);
    }

    /**
     * Converts (if necessary) the given {@link MonetaryAmount} to a
     * {@link Money} instance. The {@link MonetaryContext} will be adapted as
     * necessary, if the precision of the given amount exceeds the capabilities
     * of the default {@link MonetaryContext}.
     *
     * @param amt the amount to be converted
     * @return an according Money instance.
     */
    public static Money from(MonetaryAmount amt){
        if(amt.getClass() == Money.class){
            return (Money) amt;
        }
        return Money.of(amt.getNumber().numberValue(BigDecimal.class), amt.getCurrency(), amt.getMonetaryContext());
    }

}
