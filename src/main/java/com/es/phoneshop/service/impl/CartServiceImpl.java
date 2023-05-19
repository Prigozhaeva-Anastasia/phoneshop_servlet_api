package com.es.phoneshop.service.impl;

import com.es.phoneshop.FunctionalReadWriteLock;
import com.es.phoneshop.dao.ProductDao;
import com.es.phoneshop.dao.impl.ArrayListProductDao;
import com.es.phoneshop.exception.OutOfStockException;
import com.es.phoneshop.exception.ProductNotFoundException;
import com.es.phoneshop.model.Cart;
import com.es.phoneshop.model.CartItem;
import com.es.phoneshop.model.Product;
import com.es.phoneshop.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.util.WebUtils;

import java.util.Optional;

public class CartServiceImpl implements CartService {
    private ProductDao productDao;
    private final FunctionalReadWriteLock lock;
    private static final String SEPARATE_CART_SESSION_ATTRIBUTE = CartServiceImpl.class.getName() + ".cart";

    private CartServiceImpl() {
        this.productDao = ArrayListProductDao.getInstance();
        lock = new FunctionalReadWriteLock();
    }

    public static CartServiceImpl getInstance() {
        return CartServiceImpl.SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final CartServiceImpl INSTANCE = new CartServiceImpl();
    }

    @Override
    public void add(Cart cart, Long productId, int quantity) {
        lock.write(() -> {
            Product product = productDao.getProduct(productId);
            Optional<CartItem> foundCartItem = cart.getCartItems().stream()
                    .filter(cartItem -> cartItem.getProduct().getId().equals(product.getId()))
                    .findFirst();
            if (product.getStock() < quantity || (foundCartItem.isPresent() && foundCartItem.get().getQuantity() + quantity > product.getStock())) {
                throw new OutOfStockException("Not enough stock");
            }
            if (foundCartItem.isPresent()) {
                foundCartItem.get().setQuantity(foundCartItem.get().getQuantity() + quantity);
            }
            else {
                cart.getCartItems().add(new CartItem(product, quantity));
            }
        });
    }

    @Override
    public Cart getCart(HttpServletRequest request) {
        Cart cart = null;
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object mutex = WebUtils.getSessionMutex(session);
            synchronized (mutex) {
                cart = (Cart) session.getAttribute(SEPARATE_CART_SESSION_ATTRIBUTE);
                if (Optional.ofNullable(cart).isEmpty()) {
                    session.setAttribute(SEPARATE_CART_SESSION_ATTRIBUTE, cart = new Cart());
                }
            }
        }
        return cart;
    }
}
