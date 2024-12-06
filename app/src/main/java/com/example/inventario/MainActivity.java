package com.example.inventario;

import android.content.Intent;
import android.speech.RecognizerIntent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventario.api.ApiService;
import com.example.inventario.models.Producto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final int VOICE_REQUEST_CODE = 1001;

    private RecyclerView recyclerView;
    private TextView errorText;
    private EditText productNameInput, productDescriptionInput, productPriceInput, productQuantityInput;
    private Button addProductButton, updateProductButton, deleteProductButton, voiceAddButton;
    private ProductAdapter adapter;
    private List<Producto> productos = new ArrayList<>();
    private List<Producto> filteredProductos = new ArrayList<>();
    private int selectedProductId = -1;

    private int voiceStep = 0; // Controla el paso del comando de voz
    private String voiceProductName = "";
    private String voiceProductDescription = "";
    private String voiceProductPrice = "";
    private String voiceProductQuantity = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.productList);
        errorText = findViewById(R.id.errorText);
        productNameInput = findViewById(R.id.productNameInput);
        productDescriptionInput = findViewById(R.id.productDescriptionInput);
        productPriceInput = findViewById(R.id.productPriceInput);
        productQuantityInput = findViewById(R.id.productQuantityInput);

        addProductButton = findViewById(R.id.addProductButton);
        updateProductButton = findViewById(R.id.updateProductButton);
        deleteProductButton = findViewById(R.id.deleteProductButton);
        voiceAddButton = findViewById(R.id.voiceAddButton);

        adapter = new ProductAdapter(filteredProductos, this::onProductSelected);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fetchProducts();

        // Configurar los botones para su funcionalidad
        addProductButton.setOnClickListener(v -> addProduct());
        updateProductButton.setOnClickListener(v -> updateProduct());
        deleteProductButton.setOnClickListener(v -> deleteProduct());
        voiceAddButton.setOnClickListener(v -> {
            resetVoiceData(); // Reinicia el flujo de comando de voz
            startVoiceRecognition();
        });
    }

    private void deleteProduct() {
        if (selectedProductId == -1) {
            Toast.makeText(this, "Seleccione un producto para eliminar.", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.deleteProducto(selectedProductId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this, "Producto eliminado.", Toast.LENGTH_SHORT).show();
                    fetchProducts(); // Actualiza la lista de productos después de la eliminación
                    clearInputs(); // Limpia los campos de entrada
                } else {
                    Toast.makeText(MainActivity.this, "Error al eliminar el producto.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void fetchProducts() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.getProductos().enqueue(new Callback<List<Producto>>() {
            @Override
            public void onResponse(Call<List<Producto>> call, Response<List<Producto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Limpia y actualiza ambas listas
                    productos.clear();
                    productos.addAll(response.body());

                    filteredProductos.clear();
                    filteredProductos.addAll(productos); // Copia los datos a la lista filtrada

                    // Notifica al adaptador que los datos han cambiado
                    adapter.notifyDataSetChanged();
                    errorText.setVisibility(View.GONE); // Oculta el mensaje de error
                } else {
                    errorText.setVisibility(View.VISIBLE);
                    errorText.setText("Error en la respuesta del servidor.");
                }
            }

            @Override
            public void onFailure(Call<List<Producto>> call, Throwable t) {
                errorText.setVisibility(View.VISIBLE);
                errorText.setText("Error al conectar con la API. Verifique su conexión.");
            }
        });
    }

    private void filterProducts(String query) {
        filteredProductos.clear();
        for (Producto producto : productos) {
            if (producto.getNombre().toLowerCase().contains(query.toLowerCase()) ||
                    producto.getDescripcion().toLowerCase().contains(query.toLowerCase())) {
                filteredProductos.add(producto);
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.search);
        androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterProducts(query); // Filtrar cuando se envía el texto
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterProducts(newText); // Filtrar en tiempo real
                return false;
            }
        });

        return true;
    }


    private void updateProduct() {
        if (selectedProductId == -1) {
            Toast.makeText(this, "Seleccione un producto para modificar.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = productNameInput.getText().toString();
        String description = productDescriptionInput.getText().toString();
        String price = productPriceInput.getText().toString();
        String quantity = productQuantityInput.getText().toString();

        if (name.isEmpty() || description.isEmpty() || price.isEmpty() || quantity.isEmpty()) {
            Toast.makeText(this, "Complete todos los campos.", Toast.LENGTH_SHORT).show();
            return;
        }

        Producto updatedProduct = new Producto(
                name,
                description,
                Integer.parseInt(quantity),
                Double.parseDouble(price),
                true
        );

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.updateProducto(selectedProductId, updatedProduct).enqueue(new Callback<Producto>() {
            @Override
            public void onResponse(Call<Producto> call, Response<Producto> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this, "Producto actualizado.", Toast.LENGTH_SHORT).show();
                    fetchProducts();
                    clearInputs();
                } else {
                    Toast.makeText(MainActivity.this, "Error al actualizar producto.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Producto> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addProduct() {
        String name = productNameInput.getText().toString();
        String description = productDescriptionInput.getText().toString();
        String price = productPriceInput.getText().toString();
        String quantity = productQuantityInput.getText().toString();

        if (name.isEmpty() || description.isEmpty() || price.isEmpty() || quantity.isEmpty()) {
            Toast.makeText(this, "Complete todos los campos.", Toast.LENGTH_SHORT).show();
            return;
        }

        Producto producto = new Producto(name, description, Integer.parseInt(quantity), Double.parseDouble(price), true);

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.addProducto(producto).enqueue(new Callback<Producto>() {
            @Override
            public void onResponse(Call<Producto> call, Response<Producto> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this, "Producto agregado", Toast.LENGTH_SHORT).show();
                    fetchProducts();
                    clearInputs();
                } else {
                    Toast.makeText(MainActivity.this, "Error al agregar producto", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Producto> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getVoicePrompt());
        startActivityForResult(intent, VOICE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                handleVoiceInput(results.get(0));
            }
        }
    }

    // Método para manejar la entrada de voz
    private void handleVoiceInput(String input) {
        switch (voiceStep) {
            case 0:
                voiceProductName = input;
                voiceStep++;
                startVoiceRecognition();
                break;
            case 1:
                voiceProductDescription = input;
                voiceStep++;
                startVoiceRecognition();
                break;
            case 2:
                int price = parseNumber(input);
                if (price != -1) {
                    voiceProductPrice = String.valueOf(price);
                    voiceStep++;
                    startVoiceRecognition();
                } else {
                    Toast.makeText(this, "Por favor, diga un precio válido.", Toast.LENGTH_SHORT).show();
                }
                break;
            case 3:
                int quantity = parseNumber(input);
                if (quantity != -1) {
                    voiceProductQuantity = String.valueOf(quantity);
                    addProductFromVoice();
                } else {
                    Toast.makeText(this, "Por favor, diga una cantidad válida.", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    // Método para convertir palabras a números
    private int parseNumber(String numberWord) {
        switch (numberWord.toLowerCase()) {
            case "uno":
            case "un":
                return 1;
            case "dos":
                return 2;
            case "tres":
                return 3;
            case "cuatro":
                return 4;
            case "cinco":
                return 5;
            case "seis":
                return 6;
            case "siete":
                return 7;
            case "ocho":
                return 8;
            case "nueve":
                return 9;
            case "diez":
                return 10;
            default:
                try {
                    return Integer.parseInt(numberWord);
                } catch (NumberFormatException e) {
                    return -1;
                }
        }
    }


    private void addProductFromVoice() {
        try {
            Producto producto = new Producto(
                    voiceProductName,
                    voiceProductDescription,
                    Integer.parseInt(voiceProductQuantity),
                    Double.parseDouble(voiceProductPrice),
                    true
            );

            ApiService apiService = ApiClient.getClient().create(ApiService.class);
            apiService.addProducto(producto).enqueue(new Callback<Producto>() {
                @Override
                public void onResponse(Call<Producto> call, Response<Producto> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "Producto agregado por voz.", Toast.LENGTH_SHORT).show();
                        fetchProducts();
                        resetVoiceData();
                    } else {
                        Toast.makeText(MainActivity.this, "Error al agregar producto por voz.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Producto> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Error en los datos ingresados por voz.", Toast.LENGTH_SHORT).show();
        }
    }

    private String getVoicePrompt() {
        switch (voiceStep) {
            case 0: return "Diga el nombre del producto.";
            case 1: return "Diga la descripción del producto.";
            case 2: return "Diga el precio del producto.";
            case 3: return "Diga la cantidad del producto.";
            default: return "Error.";
        }
    }

    private void resetVoiceData() {
        voiceStep = 0; // Reinicia al primer paso
        voiceProductName = "";
        voiceProductDescription = "";
        voiceProductPrice = "";
        voiceProductQuantity = "";
    }

    private void clearInputs() {
        productNameInput.setText("");
        productDescriptionInput.setText("");
        productPriceInput.setText("");
        productQuantityInput.setText("");
        selectedProductId = -1;
    }

    private void onProductSelected(Producto producto) {
        selectedProductId = producto.getId();
        productNameInput.setText(producto.getNombre());
        productDescriptionInput.setText(producto.getDescripcion());
        productPriceInput.setText(String.valueOf(producto.getPrecio()));
        productQuantityInput.setText(String.valueOf(producto.getCantidad()));
    }
}
